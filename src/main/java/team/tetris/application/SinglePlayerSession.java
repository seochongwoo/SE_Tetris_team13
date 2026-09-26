package team.tetris.application;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import team.tetris.core.EnginePhase;
import team.tetris.core.GameAction;
import team.tetris.core.port.TetrisEnginePort;
import team.tetris.core.result.EngineStep;

/** 코어 규칙을 호출하고 시간, 점수, 한 판의 수명을 관리한다. 스레드 안전하지 않다. */
public final class SinglePlayerSession implements GameSession {
    private static final int MAX_TICKS_PER_UPDATE = 5;

    private final TetrisEnginePort engine;
    private final UUID gameId = UUID.randomUUID();
    private final ScorePolicy scorePolicy = new ScorePolicy();
    private final SpeedPolicy speedPolicy = new SpeedPolicy();
    private long score;
    private int level;
    private int clearedLines;
    private long gravityIntervalNanos = speedPolicy.gravityIntervalNanos(0);
    private long accumulatedNanos;
    private GameStatus status = GameStatus.RUNNING;
    private GameSnapshot view;
    private GameResult result;

    /** 새로 생성되어 RUNNING 상태인 엔진의 소유권을 세션에 전달한다. */
    public SinglePlayerSession(TetrisEnginePort engine) {
        this.engine = Objects.requireNonNull(engine, "engine");
        var initial = engine.snapshot();
        if (initial.phase() != EnginePhase.RUNNING) {
            throw new IllegalArgumentException("A new session requires a running engine");
        }
        view = new GameSnapshot(initial, score, level, clearedLines, gravityIntervalNanos, status);
    }

    @Override
    public void handle(GameCommand command) {
        Objects.requireNonNull(command, "command");
        if (result != null) {
            return;
        }
        if (command == GameCommand.QUIT_GAME) {
            finish(GameStatus.ABORTED);
            view = new GameSnapshot(view.engine(), score, level, clearedLines, gravityIntervalNanos, status);
            return;
        }
        if (status == GameStatus.PAUSED && command != GameCommand.RESUME) {
            return;
        }
        if (status == GameStatus.RUNNING && command == GameCommand.RESUME) {
            return;
        }
        GameAction action = switch (command) {
            case MOVE_LEFT -> GameAction.MOVE_LEFT;
            case MOVE_RIGHT -> GameAction.MOVE_RIGHT;
            case SOFT_DROP -> GameAction.SOFT_DROP;
            case HARD_DROP -> GameAction.HARD_DROP;
            case ROTATE_CW -> GameAction.ROTATE_CW;
            case PAUSE -> GameAction.PAUSE;
            case RESUME -> GameAction.RESUME;
            case QUIT_GAME -> throw new AssertionError("QUIT_GAME handled above");
        };
        if (command == GameCommand.PAUSE || command == GameCommand.RESUME) {
            accumulatedNanos = 0;
        }
        int levelBeforeStep = level;
        accept(engine.apply(action), levelBeforeStep);
    }

    @Override
    public void update(long elapsedNanos) {
        if (elapsedNanos < 0) {
            throw new IllegalArgumentException("Elapsed time must be non-negative");
        }
        if (status != GameStatus.RUNNING || elapsedNanos == 0) {
            return;
        }
        long cap = gravityIntervalNanos * MAX_TICKS_PER_UPDATE;
        // 덧셈 전에 제한하므로 Long.MAX_VALUE의 지연도 오버플로 없이 처리한다.
        accumulatedNanos = Math.min(accumulatedNanos, cap);
        accumulatedNanos += Math.min(elapsedNanos, cap - accumulatedNanos);
        for (int ticks = 0; ticks < MAX_TICKS_PER_UPDATE
                && accumulatedNanos >= gravityIntervalNanos; ticks++) {
            accumulatedNanos -= gravityIntervalNanos;
            int levelBeforeStep = level;
            EngineStep step = engine.tick();
            accept(step, levelBeforeStep);
            if (status != GameStatus.RUNNING || step.lockResult() != null) {
                return;
            }
        }
        accumulatedNanos %= gravityIntervalNanos;
    }

    private void accept(EngineStep step, int levelBeforeStep) {
        int distance = step.dropResult() == null ? 0 : step.dropResult().cellsDropped();
        int lines = step.clearResult() == null ? 0 : step.clearResult().lineCount();
        score = Math.addExact(score, scorePolicy.scoreFor(distance, lines, levelBeforeStep));
        clearedLines = Math.addExact(clearedLines, lines);
        level = speedPolicy.levelFor(clearedLines);
        gravityIntervalNanos = speedPolicy.gravityIntervalNanos(level);
        status = switch (step.snapshot().phase()) {
            case RUNNING -> GameStatus.RUNNING;
            case PAUSED -> GameStatus.PAUSED;
            case GAME_OVER -> GameStatus.GAME_OVER;
            case READY -> throw new IllegalStateException("Engine returned to READY during a game");
        };
        if (step.lockResult() != null || status != GameStatus.RUNNING) {
            accumulatedNanos = 0;
        }
        if (status == GameStatus.GAME_OVER) {
            finish(status);
        }
        view = new GameSnapshot(step.snapshot(), score, level, clearedLines, gravityIntervalNanos, status);
    }

    private void finish(GameStatus reason) {
        status = reason;
        accumulatedNanos = 0;
        result = new GameResult(gameId, score, level, clearedLines, reason);
    }

    @Override
    public GameSnapshot snapshot() {
        return view;
    }

    @Override
    public Optional<GameResult> result() {
        return Optional.ofNullable(result);
    }
}
