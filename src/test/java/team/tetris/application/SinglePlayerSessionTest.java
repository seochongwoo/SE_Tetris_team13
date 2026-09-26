package team.tetris.application;

import static org.junit.jupiter.api.Assertions.*;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import team.tetris.core.*;
import team.tetris.core.port.TetrisEnginePort;
import team.tetris.core.result.*;

class SinglePlayerSessionTest {
    private static final long SECOND = 1_000_000_000L;

    private static final class FakeEngine implements TetrisEnginePort {
        final Cell[][] board = {{Cell.EMPTY}};
        final Queue<EngineStep> steps = new ArrayDeque<>();
        final List<GameAction> actions = new ArrayList<>();
        EnginePhase phase = EnginePhase.RUNNING;
        int ticks;
        int snapshots;

        public EngineSnapshot snapshot() {
            snapshots++;
            return new EngineSnapshot(board, null, TetrominoType.I, phase);
        }

        public EngineStep apply(GameAction action) {
            actions.add(action);
            if (action == GameAction.PAUSE) phase = EnginePhase.PAUSED;
            if (action == GameAction.RESUME) phase = EnginePhase.RUNNING;
            return next(false);
        }

        public EngineStep tick() {
            ticks++;
            return next(true);
        }

        private EngineStep next(boolean falling) {
            if (!steps.isEmpty()) {
                EngineStep step = steps.remove();
                phase = step.snapshot().phase();
                return step;
            }
            return new EngineStep(snapshot(), falling ? new DropResult(1) : null, null, null);
        }

        void enqueue(int distance, int lines, boolean locked, EnginePhase after) {
            steps.add(new EngineStep(new EngineSnapshot(board, null, TetrominoType.I, after),
                    new DropResult(distance),
                    locked ? new LockResult(TetrominoType.I, new Position(0, 0), 0) : null,
                    new ClearResult(IntStream.range(0, lines).mapToObj(ClearedRow::new).toList())));
        }
    }

    @Test
    void startsRunningAndAccumulatesFractionalTimeAtExactBoundary() {
        FakeEngine engine = new FakeEngine();
        GameSession session = new SinglePlayerSession(engine);
        assertEquals(GameStatus.RUNNING, session.snapshot().status());
        assertEquals(SECOND, session.snapshot().gravityIntervalNanos());
        assertTrue(session.result().isEmpty());
        session.update(0);
        session.update(400_000_000);
        session.update(599_999_999);
        assertEquals(0, engine.ticks);
        session.update(1);
        assertEquals(1, engine.ticks);
        assertEquals(1, session.snapshot().score());
    }

    @Test
    void preservesRemainderButClampsHugeDelaysWithoutOverflow() {
        FakeEngine engine = new FakeEngine();
        GameSession session = new SinglePlayerSession(engine);
        session.update(SECOND + SECOND / 2);
        session.update(SECOND / 2);
        assertEquals(2, engine.ticks);
        session.update(SECOND - 1);
        session.update(Long.MAX_VALUE);
        assertEquals(7, engine.ticks);
        session.update(SECOND - 1);
        assertEquals(7, engine.ticks);
        session.update(1);
        assertEquals(8, engine.ticks);
    }

    @ParameterizedTest
    @EnumSource(value = GameCommand.class, names = {"MOVE_LEFT", "MOVE_RIGHT", "ROTATE_CW", "SOFT_DROP"})
    void immediateInputDoesNotResetGravity(GameCommand command) {
        FakeEngine engine = new FakeEngine();
        GameSession session = new SinglePlayerSession(engine);
        session.update(SECOND - 1);
        engine.enqueue(command == GameCommand.SOFT_DROP ? 1 : 0, 0, false, EnginePhase.RUNNING);
        session.handle(command);
        assertEquals(List.of(GameAction.valueOf(command.name())), engine.actions);
        assertEquals(0, engine.ticks);
        session.update(1);
        assertEquals(1, engine.ticks);
    }

    @Test
    void pauseBlocksInputAndTimeAndResumeStartsFullInterval() {
        FakeEngine engine = new FakeEngine();
        GameSession session = new SinglePlayerSession(engine);
        session.handle(GameCommand.RESUME);
        assertTrue(engine.actions.isEmpty());
        session.update(SECOND - 1);
        session.handle(GameCommand.PAUSE);
        assertEquals(GameStatus.PAUSED, session.snapshot().status());
        assertEquals(EnginePhase.PAUSED, session.snapshot().engine().phase());
        for (GameCommand command : GameCommand.values()) {
            if (command != GameCommand.RESUME && command != GameCommand.QUIT_GAME) session.handle(command);
        }
        session.update(Long.MAX_VALUE);
        assertEquals(List.of(GameAction.PAUSE), engine.actions);
        session.handle(GameCommand.RESUME);
        assertEquals(GameStatus.RUNNING, session.snapshot().status());
        assertEquals(EnginePhase.RUNNING, session.snapshot().engine().phase());
        session.update(SECOND - 1);
        assertEquals(0, engine.ticks);
        session.update(1);
        assertEquals(1, engine.ticks);
    }

    @ParameterizedTest
    @EnumSource(value = GameCommand.class, names = {"HARD_DROP", "SOFT_DROP"})
    void inputLockGivesNextPieceFullInterval(GameCommand command) {
        FakeEngine engine = new FakeEngine();
        GameSession session = new SinglePlayerSession(engine);
        session.update(SECOND - 1);
        engine.enqueue(0, 0, true, EnginePhase.RUNNING);
        session.handle(command);
        session.update(SECOND - 1);
        assertEquals(0, engine.ticks);
        assertEquals(0, session.snapshot().score());
        session.update(1);
        assertEquals(1, engine.ticks);
    }

    @Test
    void gravityLockStopsCatchUpAndDiscardsRemainingTime() {
        FakeEngine engine = new FakeEngine();
        GameSession session = new SinglePlayerSession(engine);
        engine.enqueue(0, 0, true, EnginePhase.RUNNING);
        session.update(5 * SECOND);
        assertEquals(1, engine.ticks);
        session.update(SECOND - 1);
        assertEquals(1, engine.ticks);
        session.update(1);
        assertEquals(2, engine.ticks);
    }

    @Test
    void tenthLineScoresAtPreviousLevelAndNextDropUsesNewSpeedAndMultiplier() {
        FakeEngine engine = new FakeEngine();
        GameSession session = new SinglePlayerSession(engine);
        for (int lines : new int[]{4, 4, 1}) {
            engine.enqueue(0, lines, true, EnginePhase.RUNNING);
            session.handle(GameCommand.HARD_DROP);
        }
        assertEquals(9, session.snapshot().clearedLines());
        long previousScore = session.snapshot().score();
        engine.enqueue(3, 1, true, EnginePhase.RUNNING);
        session.handle(GameCommand.HARD_DROP);
        assertEquals(previousScore + 103, session.snapshot().score());
        assertEquals(10, session.snapshot().clearedLines());
        assertEquals(1, session.snapshot().level());
        assertEquals(900_000_000, session.snapshot().gravityIntervalNanos());
        session.update(899_999_999);
        assertEquals(0, engine.ticks);
        session.update(1);
        assertEquals(previousScore + 105, session.snapshot().score());
    }

    @Test
    void lastStepScoresBeforeGameOverAndStopsAllFurtherEngineCalls() {
        FakeEngine engine = new FakeEngine();
        GameSession session = new SinglePlayerSession(engine);
        engine.enqueue(3, 1, false, EnginePhase.GAME_OVER);
        session.update(Long.MAX_VALUE);
        assertEquals(1, engine.ticks);
        assertEquals(103, session.snapshot().score());
        assertNull(session.snapshot().engine().activePiece());
        GameResult result = session.result().orElseThrow();
        assertEquals(103, result.score());
        assertEquals(1, result.clearedLines());
        assertEquals(GameStatus.GAME_OVER, result.reason());
        assertTerminalIsFrozen(session, engine, result);
    }

    @ParameterizedTest
    @EnumSource(value = GameStatus.class, names = {"RUNNING", "PAUSED"})
    void quitFromRunningOrPausedFreezesResult(GameStatus initial) {
        FakeEngine engine = new FakeEngine();
        GameSession session = new SinglePlayerSession(engine);
        session.update(SECOND);
        if (initial == GameStatus.PAUSED) session.handle(GameCommand.PAUSE);
        session.handle(GameCommand.QUIT_GAME);
        GameResult result = session.result().orElseThrow();
        assertEquals(GameStatus.ABORTED, result.reason());
        assertEquals(1, result.score());
        assertTerminalIsFrozen(session, engine, result);
    }

    private static void assertTerminalIsFrozen(GameSession session, FakeEngine engine, GameResult result) {
        int calls = engine.ticks + engine.actions.size() + engine.snapshots;
        GameSnapshot before = session.snapshot();
        for (GameCommand command : GameCommand.values()) session.handle(command);
        session.update(Long.MAX_VALUE);
        assertSame(before, session.snapshot());
        assertSame(result, session.result().orElseThrow());
        assertEquals(calls, engine.ticks + engine.actions.size() + engine.snapshots);
        assertThrows(NullPointerException.class, () -> session.handle(null));
        assertThrows(IllegalArgumentException.class, () -> session.update(-1));
    }

    @Test
    void rejectsInvalidArgumentsAndNonRunningEngine() {
        assertThrows(NullPointerException.class, () -> new SinglePlayerSession(null));
        FakeEngine engine = new FakeEngine();
        GameSession session = new SinglePlayerSession(engine);
        assertThrows(NullPointerException.class, () -> session.handle(null));
        assertThrows(IllegalArgumentException.class, () -> session.update(-1));
        for (EnginePhase phase : List.of(EnginePhase.READY, EnginePhase.PAUSED, EnginePhase.GAME_OVER)) {
            engine.phase = phase;
            assertThrows(IllegalArgumentException.class, () -> new SinglePlayerSession(engine));
        }
    }

    @Test
    void snapshotCopiesInputAndOutputArraysAndKeepsEarlierViewStable() {
        FakeEngine engine = new FakeEngine();
        GameSession session = new SinglePlayerSession(engine);
        GameSnapshot before = session.snapshot();
        engine.board[0][0] = Cell.occupiedBy(TetrominoType.I);
        assertEquals(Cell.EMPTY, before.engine().board()[0][0]);
        EngineSnapshot exposed = before.engine();
        exposed.board()[0][0] = Cell.occupiedBy(TetrominoType.O);
        exposed.board()[0] = new Cell[0];
        assertEquals(Cell.EMPTY, before.engine().board()[0][0]);
        session.update(SECOND);
        assertEquals(0, before.score());
        assertEquals(Cell.occupiedBy(TetrominoType.I), session.snapshot().engine().board()[0][0]);
    }
}
