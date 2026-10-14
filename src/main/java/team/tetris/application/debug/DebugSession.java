package team.tetris.application.debug;

import java.util.Objects;
import java.util.Optional;
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
        session.observeSteps(step -> record(event, step));
        record("APPLY_SETUP", null);
    }

    public DebugSetup setup() { return setup; }
    public GameMode mode() { return mode; }
    public Difficulty difficulty() { return difficulty; }
    public boolean frozen() { return frozen; }
    public String logDescription() { return log.description(); }

    public void setFrozen(boolean value) {
        if (frozen == value) return;
        session.resetGravityClock();
        frozen = value;
        record("SET_FROZEN " + value, null);
    }

    public void stepGravity() {
        setFrozen(true);
        event = "STEP_GRAVITY";
        long before = log.sequence();
        session.stepGravity();
        if (log.sequence() == before) record("STEP_GRAVITY ignored", null);
    }

    public void replaceNext(TetrominoType type, DebugItem item) {
        if (session.result().isEmpty()) {
            source.replaceNext(item.piece(type));
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
        record("APPLY_STATS", null);
    }

    @Override public void handle(GameCommand command) {
        event = "COMMAND " + command;
        long before = log.sequence();
        session.handle(command);
        if (log.sequence() == before) record(event, null);
    }

    @Override public void update(long elapsedNanos) {
        if (elapsedNanos < 0) throw new IllegalArgumentException("Elapsed time must be non-negative");
        event = "GRAVITY";
        if (!frozen) session.update(elapsedNanos);
    }

    @Override public GameSnapshot snapshot() {
        GameSnapshot state = session.snapshot();
        return new GameSnapshot(engine.snapshot(), state.score(), state.level(), state.clearedLines(),
                state.gravityIntervalNanos(), state.status(), state.doubleScoreClears());
    }

    @Override public Optional<GameResult> result() { return session.result(); }

    private void record(String cause, EngineStep step) {
        log.write(cause, snapshot(), difficulty, frozen, session.accumulatedGravityNanos(),
                itemSource == null ? "plain" : itemSource.debugState(), step);
    }
}
