package team.tetris.application.debug;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import team.tetris.application.*;
import team.tetris.application.model.Difficulty;
import team.tetris.application.model.GameMode;
import team.tetris.application.port.DebugLogSink;
import team.tetris.core.TetrominoType;
import team.tetris.core.port.TetrisEnginePort;
import team.tetris.core.result.EngineStep;
import team.tetris.core.rule.DebugPieceSource;
import team.tetris.core.rule.ItemPieceSource;

/** 실제 게임 규칙을 사용하며 시간·블록·스탯을 제어하고 동작 결과를 기록한다. */
public final class DebugSession implements GameSession {
    private final SinglePlayerSession session;
    private final TetrisEnginePort engine;
    private final DebugPieceSource source;
    private final DebugPieceGenerator generator;
    private final ItemPieceSource itemSource;
    private final DebugSetup setup;
    private final GameMode mode;
    private final DebugStateLog log;
    /** 같은 시드에서 조작을 재생해 아이템 추첨 상태까지 복원한다. */
    private final List<Consumer<DebugSession>> history = new ArrayList<>();
    private int lastPlacementIndex = -1;
    private boolean replaying;
    private Difficulty difficulty;
    private boolean frozen = true;
    private String event = "GRAVITY";

    public DebugSession(SinglePlayerSession session, TetrisEnginePort engine, DebugPieceSource source,
                        DebugPieceGenerator generator, ItemPieceSource itemSource, DebugSetup setup,
                        GameMode mode, DebugLogSink sink) {
        this.session = session;
        this.engine = engine;
        this.source = source;
        this.generator = generator;
        this.itemSource = itemSource;
        this.setup = setup;
        this.mode = mode;
        this.difficulty = setup.difficulty();
        this.log = new DebugStateLog(sink, setup, mode);
        session.observeSteps(step -> {
            if (step.lockResult() != null) lastPlacementIndex = history.size() - 1;
            record(event, step);
        });
        record("APPLY_SETUP", null);
    }

    public DebugSetup setup() { return setup; }
    public GameMode mode() { return mode; }
    public Difficulty difficulty() { return difficulty; }
    public boolean frozen() { return frozen; }
    public String logDescription() { return log.description(); }

    public boolean canUndoPlacement() { return lastPlacementIndex >= 0; }

    /** 새 세션에 마지막 블록을 고정한 조작 직전까지 재생하고 자동 낙하를 멈춘다. */
    public void replayBeforeLastPlacement(DebugSession target) {
        if (!canUndoPlacement()) throw new IllegalStateException("되돌릴 블록 배치가 없습니다.");
        if (target == this || !target.history.isEmpty() || !setup.equals(target.setup) || mode != target.mode) {
            throw new IllegalArgumentException("같은 설정의 새 디버그 세션이 필요합니다.");
        }
        target.replaying = true;
        try {
            for (int i = 0; i < lastPlacementIndex; i++) history.get(i).accept(target);
        } finally {
            target.replaying = false;
        }
        target.setFrozen(true);
        target.record("UNDO_PLACEMENT", null);
    }

    public void setFrozen(boolean value) {
        if (frozen == value) return;
        history.add(target -> target.setFrozen(value));
        session.resetGravityClock();
        frozen = value;
        record("SET_FROZEN " + value, null);
    }

    public void stepGravity() {
        setFrozen(true);
        history.add(DebugSession::stepGravity);
        event = "STEP_GRAVITY";
        long before = log.sequence();
        session.stepGravity();
        if (log.sequence() == before) record("STEP_GRAVITY ignored", null);
    }

    public void replaceNext(TetrominoType type, DebugItem item) {
        if (session.result().isEmpty()) {
            source.replaceNext(item.piece(type));
            history.add(target -> target.replaceNext(type, item));
            record("REPLACE_NEXT " + type + " " + item, null);
        }
    }

    public void applyStats(int lines, Difficulty nextDifficulty) {
        DebugSetup.validateLines(lines);
        Objects.requireNonNull(nextDifficulty, "difficulty");
        session.configureProgress(lines, nextDifficulty, new SpeedPolicy(nextDifficulty));
        if (itemSource != null) itemSource.resetLineProgress(lines);
        if (difficulty != nextDifficulty) {
            var preview = source.peek();
            generator.setDifficulty(nextDifficulty);
            source.replaceNext(preview);
        }
        difficulty = nextDifficulty;
        frozen = true;
        history.add(target -> target.applyStats(lines, nextDifficulty));
        record("APPLY_STATS", null);
    }

    @Override public void handle(GameCommand command) {
        Objects.requireNonNull(command, "command");
        history.add(target -> target.handle(command));
        event = "COMMAND " + command;
        long before = log.sequence();
        session.handle(command);
        if (log.sequence() == before) record(event, null);
    }

    @Override public void update(long elapsedNanos) {
        if (elapsedNanos < 0) throw new IllegalArgumentException("Elapsed time must be non-negative");
        event = "GRAVITY";
        // 자동 낙하를 멈춰도 삭제 연출은 끝까지 진행한다. 일시정지는 연출도 멈춘다.
        if ((!frozen || session.snapshot().lineClearAnimation() != null)
                && elapsedNanos > 0 && session.snapshot().status() == GameStatus.RUNNING) {
            history.add(target -> target.update(elapsedNanos));
            session.update(elapsedNanos);
        }
    }

    @Override public GameSnapshot snapshot() {
        GameSnapshot state = session.snapshot();
        return new GameSnapshot(engine.snapshot(), state.score(), state.level(), state.clearedLines(),
                state.gravityIntervalNanos(), state.status(), state.doubleScoreClears(), state.lineClearAnimation());
    }

    @Override public Optional<GameResult> result() { return session.result(); }

    private void record(String cause, EngineStep step) {
        if (replaying) return;
        log.write(cause, snapshot(), difficulty, frozen, session.accumulatedGravityNanos(),
                itemSource == null ? "plain" : itemSource.debugState(), step);
    }
}
