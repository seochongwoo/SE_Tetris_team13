package team.tetris.application;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import team.tetris.application.model.GameMode;
import team.tetris.application.model.Difficulty;
import team.tetris.core.EnginePhase;
import team.tetris.core.GameAction;
import team.tetris.core.item.DoubleScoreItem;
import team.tetris.core.port.TetrisEnginePort;
import team.tetris.core.result.ClearedRow;
import team.tetris.core.result.EngineStep;
import team.tetris.core.result.ItemActivation;

/** 코어 호출과 시간·점수·한 판의 수명 관리. 하나의 실행 흐름에서 호출 필요. */
public final class SinglePlayerSession implements GameSession {
    private static final int MAX_TICKS_PER_UPDATE = 5;
    /** 점수 2배 아이템({@code $}) 하나가 적용되는 줄 삭제 횟수. */
    public static final int DOUBLE_SCORE_CLEARS = 3;
    /** 줄 삭제 애니메이션 시간. 이 동안 지운 줄이 반짝이고 블록은 내려오지 않는다. */
    public static final long LINE_CLEAR_FLASH_NANOS = 300_000_000L;

    private final TetrisEnginePort engine;
    private final UUID gameId = UUID.randomUUID();
    private final ScoreRule scorePolicy;
    private final SpeedRule speedPolicy;
    private final GameMode mode;
    private final Difficulty difficulty;
    private long score;
    private int level;
    private int clearedLines;
    private int doubleScoreClears;
    private List<ClearedRow> clearingRows = List.of();
    private long clearingNanos;
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
        view = new GameSnapshot(initial, score, level, clearedLines, gravityIntervalNanos, status, doubleScoreClears);
    }

    @Override
    public void handle(GameCommand command) {
        Objects.requireNonNull(command, "command");
        if (result != null) {
            return;
        }
        // 애니메이션 중에 들어온 명령은 버리지 않는다: 애니메이션을 끝내고 바로 처리한다.
        endLineClear();
        if (command == GameCommand.QUIT_GAME) {
            finish(GameStatus.ABORTED);
            view = new GameSnapshot(view.engine(), score, level, clearedLines, gravityIntervalNanos, status, doubleScoreClears);
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
        if (!clearingRows.isEmpty()) {
            // 애니메이션 동안의 시간은 중력에 쓰지 않는다 (줄이 지워진 고정 직후라 쌓인 시간도 없다).
            if (elapsedNanos < clearingNanos) {
                clearingNanos -= elapsedNanos;
            } else {
                endLineClear();
            }
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
        int nextDoubleClears = doubleScoreClears;
        if (activatesDoubleScore(step)) {
            nextDoubleClears = DOUBLE_SCORE_CLEARS; // 중복 획득 시 남은 횟수를 다시 채운다 (누적하지 않음)
        }
        if (lines > 0 && nextDoubleClears > 0) {
            gained = Math.multiplyExact(gained, 2L);
            nextDoubleClears--;
        }
        long nextScore = Math.addExact(score, gained);
        int nextLines = Math.addExact(clearedLines, lines);
        int nextLevel = checkedLevel(nextLines);
        long nextInterval = checkedInterval(nextLevel);
        score = nextScore;
        doubleScoreClears = nextDoubleClears;
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
        if (lines > 0 && status == GameStatus.RUNNING) {
            clearingRows = step.clearResult().clearedRows();
            clearingNanos = LINE_CLEAR_FLASH_NANOS;
        }
        view = new GameSnapshot(step.snapshot(), score, level, clearedLines, gravityIntervalNanos, status,
                doubleScoreClears, clearingRows);
    }

    /** 줄 삭제 애니메이션을 끝내고 화면을 실제 보드로 되돌린다. 애니메이션 중이 아니면 아무것도 하지 않는다. */
    private void endLineClear() {
        if (clearingRows.isEmpty()) {
            return;
        }
        clearingRows = List.of();
        clearingNanos = 0;
        view = new GameSnapshot(view.engine(), score, level, clearedLines, gravityIntervalNanos, status, doubleScoreClears);
    }

    /**
     * 이번 결과에 점수 2배 아이템 발동이 있는가. 아이템이 붙은 블록이 줄을 지우면 그 삭제부터 2배가
     * 적용된다.
     */
    private static boolean activatesDoubleScore(EngineStep step) {
        for (ItemActivation activation : step.itemActivations()) {
            if (activation.item() instanceof DoubleScoreItem) {
                return true;
            }
        }
        return false;
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

    @Override
    public GameMode mode() {
        return mode;
    }

    @Override
    public Difficulty difficulty() {
        return difficulty;
    }
}
