package team.tetris.application.battle;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiFunction;
import team.tetris.application.GameCommand;
import team.tetris.application.GameSnapshot;
import team.tetris.application.GameStatus;
import team.tetris.application.ScoreRule;
import team.tetris.application.SinglePlayerSession;
import team.tetris.application.SpeedRule;
import team.tetris.application.model.Difficulty;
import team.tetris.application.model.GameMode;
import team.tetris.core.Cell;
import team.tetris.core.EnginePhase;
import team.tetris.core.port.TetrisEnginePort;
import team.tetris.core.result.EngineStep;
import team.tetris.core.rule.AttackQueue;
import team.tetris.core.rule.AttackRule;
import team.tetris.core.rule.IncomingLines;

/**
 * 한 컴퓨터에서 두 명이 하는 대전 (3차 카드 17). 플레이어마다 1인 게임과 같은 세션을 하나씩 두고,
 * 한쪽이 2줄 이상 지우면 공격 줄을 상대의 대기열에 넣는다. 상대는 다음 블록을 고정할 때 그 줄을 받는다.
 *
 * <ul>
 *   <li>승패는 논리적 패배 순간에 확정한다. 1인 게임처럼 줄 삭제 연출이 끝날 때까지 기다리지 않는다.</li>
 *   <li>한 프레임({@link #update} 한 번)에 둘 다 쌓을 수 없게 되면 무승부 (안건 ⑬). 조작({@link #handle})은
 *       그 플레이어 보드만 바꾸므로 조작으로 진 경우는 그 자리에서 확정한다.</li>
 *   <li>한 프레임 안에서는 1P를 먼저 진행하지만, 그 프레임에 생긴 공격은 두 보드를 모두 진행한 뒤에
 *       넣는다. 그래서 같은 프레임의 공격을 1P만 먼저 받거나 2P만 먼저 받는 일이 없다.</li>
 *   <li>결과가 나오면 두 세션을 더 진행하지 않아 이후 점수·공격이 생기지 않는다 (안건 ㉓). 진행 중이던
 *       줄 삭제 연출은 화면에서 지운다.</li>
 *   <li>한 명이 정지하면 둘 다 정지하고, 나가면 나간 사람이 진다 (안건 ⑱).</li>
 *   <li>결과는 스코어보드에 기록하지 않는다. 이 세션은 기록 서비스를 모른다.</li>
 * </ul>
 */
public final class LocalBattleSession implements BattleSession {

    private final Map<BattlePlayer, SinglePlayerSession> sessions = new EnumMap<>(BattlePlayer.class);
    /** 플레이어 보드에 들어가기를 기다리는 공격 줄. */
    private final Map<BattlePlayer, AttackQueue> incoming = new EnumMap<>(BattlePlayer.class);
    private final GameMode mode;
    private final Difficulty difficulty;
    private BattleStatus status = BattleStatus.RUNNING;
    private BattleResult result;
    /** {@link #update} 도중에 생긴 공격. 두 보드를 모두 진행한 뒤 넣는다. 그 밖에는 null. */
    private Map<BattlePlayer, List<List<Cell>>> frameAttacks;

    /**
     * @param width   두 보드의 폭 (공격 줄 검사용)
     * @param engines 플레이어와 그 플레이어가 받을 공격 줄로 새 RUNNING 엔진을 만든다
     */
    public LocalBattleSession(int width, BiFunction<BattlePlayer, IncomingLines, TetrisEnginePort> engines,
                              ScoreRule scoring, SpeedRule speed, GameMode mode, Difficulty difficulty) {
        Objects.requireNonNull(engines, "engines");
        this.mode = Objects.requireNonNull(mode, "mode");
        this.difficulty = Objects.requireNonNull(difficulty, "difficulty");
        for (BattlePlayer player : BattlePlayer.values()) {
            AttackQueue queue = new AttackQueue(width);
            TetrisEnginePort engine = Objects.requireNonNull(engines.apply(player, queue), "engine");
            int engineWidth = engine.snapshot().board()[0].length;
            if (engineWidth != width) {
                throw new IllegalArgumentException("Board width " + engineWidth + " differs from " + width);
            }
            SinglePlayerSession session = new SinglePlayerSession(engine, scoring, speed, mode, difficulty);
            session.observeSteps(step -> sendAttack(player, step));
            incoming.put(player, queue);
            sessions.put(player, session);
        }
    }

    @Override
    public void handle(BattlePlayer player, GameCommand command) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(command, "command");
        if (result != null) {
            return;
        }
        switch (command) {
            case QUIT_GAME -> finish(player.opponent(), BattleEnd.FORFEIT);
            case PAUSE -> {
                if (status == BattleStatus.RUNNING) {
                    sessions.values().forEach(session -> session.handle(GameCommand.PAUSE));
                    status = BattleStatus.PAUSED;
                }
            }
            case RESUME -> {
                if (status == BattleStatus.PAUSED) {
                    sessions.values().forEach(session -> session.handle(GameCommand.RESUME));
                    status = BattleStatus.RUNNING;
                }
            }
            default -> {
                if (status != BattleStatus.RUNNING) {
                    return;
                }
                sessions.get(player).handle(command);
                if (toppedOut(player)) {
                    finish(player.opponent(), BattleEnd.TOPPED_OUT);
                }
            }
        }
    }

    @Override
    public void abort() {
        if (result == null) {
            finish(null, BattleEnd.ABORTED);
        }
    }

    @Override
    public void update(long elapsedNanos) {
        if (elapsedNanos < 0) {
            throw new IllegalArgumentException("Elapsed time must be non-negative");
        }
        if (status != BattleStatus.RUNNING) {
            return;
        }
        Map<BattlePlayer, List<List<Cell>>> attacks = new EnumMap<>(BattlePlayer.class);
        frameAttacks = attacks;
        try {
            for (SinglePlayerSession session : sessions.values()) {
                session.update(elapsedNanos);
            }
        } finally {
            frameAttacks = null;
        }
        boolean oneLost = toppedOut(BattlePlayer.ONE);
        boolean twoLost = toppedOut(BattlePlayer.TWO);
        if (!oneLost && !twoLost) {
            attacks.forEach((receiver, lines) -> incoming.get(receiver).add(lines));
        } else if (oneLost && twoLost) {
            finish(null, BattleEnd.BOTH_TOPPED_OUT);
        } else if (oneLost || twoLost) {
            finish(oneLost ? BattlePlayer.TWO : BattlePlayer.ONE, BattleEnd.TOPPED_OUT);
        }
    }

    /** 2줄 이상 지운 고정이면 상대의 대기열에 공격 줄을 넣는다. 결과가 나온 뒤에는 보내지 않는다. */
    private void sendAttack(BattlePlayer attacker, EngineStep step) {
        if (result != null) {
            return;
        }
        List<List<Cell>> lines = AttackRule.linesFor(step.lockResult(), step.clearResult());
        if (lines.isEmpty()) {
            return;
        }
        BattlePlayer receiver = attacker.opponent();
        if (frameAttacks == null) {
            incoming.get(receiver).add(lines);
        } else {
            // 한 프레임에 여러 번 지웠으면 생긴 순서대로 이어 붙인다. 10줄 제한은 대기열이 적용한다.
            frameAttacks.computeIfAbsent(receiver, ignored -> new java.util.ArrayList<>()).addAll(lines);
        }
    }

    /** 1인 게임은 연출 뒤에 종료를 알리지만, 대전은 엔진이 더 쌓을 수 없게 된 순간을 패배로 본다. */
    private boolean toppedOut(BattlePlayer player) {
        return sessions.get(player).snapshot().engine().phase() == EnginePhase.GAME_OVER;
    }

    private void finish(BattlePlayer winner, BattleEnd end) {
        status = BattleStatus.FINISHED;
        result = new BattleResult(winner, end, scoreOf(BattlePlayer.ONE), scoreOf(BattlePlayer.TWO));
    }

    private long scoreOf(BattlePlayer player) {
        return sessions.get(player).snapshot().score();
    }

    @Override
    public BattleSnapshot snapshot() {
        return new BattleSnapshot(view(BattlePlayer.ONE), view(BattlePlayer.TWO),
                incoming.get(BattlePlayer.ONE).pending(), incoming.get(BattlePlayer.TWO).pending(),
                status, result, mode, difficulty);
    }

    /**
     * 끝난 뒤에는 멈춘 줄 삭제 연출 대신 확정된 보드를 보여 준다. 연출 때문에 1인 세션이 아직 진행 중으로
     * 알리고 있어도, 쌓을 수 없게 된 플레이어는 GAME_OVER로 보여 준다.
     */
    private GameSnapshot view(BattlePlayer player) {
        GameSnapshot view = sessions.get(player).snapshot();
        if (result == null) {
            return view;
        }
        GameStatus shown = toppedOut(player) ? GameStatus.GAME_OVER : view.status();
        if (view.lineClearAnimation() == null && shown == view.status()) {
            return view;
        }
        return new GameSnapshot(view.engine(), view.score(), view.level(), view.clearedLines(),
                view.gravityIntervalNanos(), shown, view.doubleScoreClears(), null,
                view.mode(), view.difficulty());
    }

    @Override
    public Optional<BattleResult> result() {
        return Optional.ofNullable(result);
    }
}
