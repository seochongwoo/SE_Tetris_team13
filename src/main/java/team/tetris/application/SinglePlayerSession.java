package team.tetris.application;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import team.tetris.application.model.GameMode;
import team.tetris.application.model.Difficulty;
import team.tetris.core.EnginePhase;
import team.tetris.core.GameAction;
import team.tetris.core.port.TetrisEnginePort;
import team.tetris.core.result.EngineStep;

/** 코어 호출과 시간·점수·한 판의 수명 관리. 하나의 실행 흐름에서 호출 필요. */
public final class SinglePlayerSession implements GameSession {
    private static final int MAX_TICKS_PER_UPDATE = 5;

    private final TetrisEnginePort engine;
    private final UUID gameId = UUID.randomUUID();
    private final ScoreRule scorePolicy;
    private final SpeedRule speedPolicy;
    private final GameMode mode;
    private final Difficulty difficulty;
    private long score;
    private int level;
    private int clearedLines;
    private long gravityIntervalNanos;
    private long accumulatedNanos;
    private GameStatus status = GameStatus.RUNNING;
    private GameSnapshot view;
    private GameResult result;

    /** 기본 정책으로 새 RUNNING 엔진의 소유권을 세션에 전달. */
    public SinglePlayerSession(TetrisEnginePort engine) {
        this(engine, new ScorePolicy(), new SpeedPolicy());
    }

    /** 점수·속도 규칙 주입. 엔진과 정책의 외부 변경 금지. */
    public SinglePlayerSession(TetrisEnginePort engine, ScoreRule scorePolicy, SpeedRule speedPolicy) {
        this(engine, scorePolicy, speedPolicy, GameMode.NORMAL, Difficulty.NORMAL);
    }

    /** 모드·난이도는 시작할 때 확정하고 종료 결과까지 유지한다. */
    public SinglePlayerSession(TetrisEnginePort engine, ScoreRule scorePolicy, SpeedRule speedPolicy,
                               GameMode mode, Difficulty difficulty) {
        this.mode = Objects.requireNonNull(mode, "mode");
        this.difficulty = Objects.requireNonNull(difficulty, "difficulty");
        this.engine = Objects.requireNonNull(engine, "engine");
        this.scorePolicy = Objects.requireNonNull(scorePolicy, "scorePolicy");
        this.speedPolicy = Objects.requireNonNull(speedPolicy, "speedPolicy");
        level = checkedLevel(0);
        gravityIntervalNanos = checkedInterval(level);
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
        long cap = gravityIntervalNanos > Long.MAX_VALUE / MAX_TICKS_PER_UPDATE
                ? Long.MAX_VALUE : gravityIntervalNanos * MAX_TICKS_PER_UPDATE;
        // 곱셈·덧셈 전 상한 적용으로 긴 낙하 간격과 지연의 오버플로 방지.
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
        long gained = scorePolicy.scoreFor(distance, lines, levelBeforeStep);
        if (gained < 0) {
            throw new IllegalStateException("Score rule must return a non-negative score");
        }
        long nextScore = Math.addExact(score, gained);
        int nextLines = Math.addExact(clearedLines, lines);
        int nextLevel = checkedLevel(nextLines);
        long nextInterval = checkedInterval(nextLevel);
        score = nextScore;
        clearedLines = nextLines;
        level = nextLevel;
        gravityIntervalNanos = nextInterval;
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

    private int checkedLevel(int lines) {
        int value = speedPolicy.levelFor(lines);
        if (value < 0) throw new IllegalStateException("Speed rule must return a non-negative level");
        return value;
    }

    private long checkedInterval(int currentLevel) {
        long value = speedPolicy.gravityIntervalNanos(currentLevel);
        if (value <= 0) throw new IllegalStateException("Speed rule must return a positive interval");
        return value;
    }

    private void finish(GameStatus reason) {
        status = reason;
        accumulatedNanos = 0;
        result = new GameResult(gameId, score, level, clearedLines, reason, mode, difficulty);
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
