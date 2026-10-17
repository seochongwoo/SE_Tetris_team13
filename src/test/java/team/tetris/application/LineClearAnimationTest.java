package team.tetris.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import org.junit.jupiter.api.Test;
import team.tetris.application.model.Difficulty;
import team.tetris.application.model.GameMode;
import team.tetris.core.Cell;
import team.tetris.core.EnginePhase;
import team.tetris.core.EngineSnapshot;
import team.tetris.core.GameAction;
import team.tetris.core.Piece;
import team.tetris.core.Position;
import team.tetris.core.TetrominoType;
import team.tetris.core.port.TetrisEnginePort;
import team.tetris.core.result.ClearResult;
import team.tetris.core.result.ClearedRow;
import team.tetris.core.result.DropResult;
import team.tetris.core.result.EngineStep;
import team.tetris.core.result.LockResult;

/** 줄이 지워지면 세션이 잠깐 애니메이션 상태가 되고, 그동안 블록이 내려오지 않는다. */
class LineClearAnimationTest {

    private static final long FLASH = SinglePlayerSession.LINE_CLEAR_FLASH_NANOS;
    private static final long SECOND = 1_000_000_000L;

    private final StepEngine engine = new StepEngine();
    private final SinglePlayerSession session = new SinglePlayerSession(engine);

    /** rows 줄을 지우는 고정 한 번. */
    private void clear(EnginePhase after, int... rows) {
        List<ClearedRow> cleared = new ArrayList<>();
        for (int row : rows) {
            cleared.add(new ClearedRow(row, List.of(Cell.occupiedBy(TetrominoType.I))));
        }
        engine.queue.add(new EngineStep(engine.snapshotIn(after), new DropResult(0),
                new LockResult(TetrominoType.I, new Position(0, 0), 0), new ClearResult(cleared)));
        session.handle(GameCommand.HARD_DROP);
    }

    @Test
    void clearedRowsStartAShortFlashThatHoldsGravity() {
        clear(EnginePhase.RUNNING, 18, 19);

        GameSnapshot flashing = session.snapshot();
        assertTrue(flashing.isClearing());
        assertEquals(List.of(18, 19), flashing.clearingRows().stream().map(ClearedRow::rowIndex).toList());
        assertEquals(2, flashing.clearedLines(), "줄 수와 점수는 지운 순간 바로 반영");

        session.update(FLASH - 1);
        assertTrue(session.snapshot().isClearing());
        assertEquals(0, engine.ticks, "애니메이션 동안 블록이 내려오지 않는다");

        session.update(1);
        assertFalse(session.snapshot().isClearing());
        assertEquals(0, engine.ticks);

        session.update(SECOND);
        assertEquals(1, engine.ticks, "애니메이션이 끝나면 중력이 다시 동작한다");
    }

    @Test
    void lockingWithoutClearingLinesHasNoFlash() {
        clear(EnginePhase.RUNNING);

        assertFalse(session.snapshot().isClearing());
        session.update(SECOND);
        assertEquals(1, engine.ticks);
    }

    @Test
    void aCommandDuringTheFlashEndsItAndIsStillHandled() {
        clear(EnginePhase.RUNNING, 19);

        session.handle(GameCommand.MOVE_LEFT);

        assertFalse(session.snapshot().isClearing());
        assertEquals(List.of(GameAction.HARD_DROP, GameAction.MOVE_LEFT), engine.actions);
    }

    @Test
    void pausingDuringTheFlashEndsItAndPauses() {
        clear(EnginePhase.RUNNING, 19);

        session.handle(GameCommand.PAUSE);

        assertFalse(session.snapshot().isClearing());
        assertEquals(GameStatus.PAUSED, session.snapshot().status());
    }

    @Test
    void aClearThatEndsTheGameHasNoFlash() {
        clear(EnginePhase.GAME_OVER, 19);

        assertFalse(session.snapshot().isClearing());
        assertEquals(GameStatus.GAME_OVER, session.snapshot().status());
    }

    @Test
    void theSessionReportsTheModeAndDifficultyItStartedWith() {
        SinglePlayerSession item = new SinglePlayerSession(new StepEngine(), new ScorePolicy(),
                new SpeedPolicy(Difficulty.HARD), GameMode.ITEM, Difficulty.HARD);

        assertEquals(GameMode.ITEM, item.mode());
        assertEquals(Difficulty.HARD, item.difficulty());
        assertEquals(GameMode.NORMAL, session.mode());
        assertEquals(Difficulty.NORMAL, session.difficulty());
    }

    /** 넣어 둔 결과를 차례로 돌려주고, 없으면 한 칸 내려간 결과를 돌려주는 엔진. */
    private static final class StepEngine implements TetrisEnginePort {
        final Queue<EngineStep> queue = new ArrayDeque<>();
        final List<GameAction> actions = new ArrayList<>();
        EnginePhase phase = EnginePhase.RUNNING;
        int ticks;

        EngineSnapshot snapshotIn(EnginePhase phase) {
            return new EngineSnapshot(new Cell[][] {{Cell.EMPTY}}, null, Piece.of(TetrominoType.I), phase);
        }

        @Override
        public EngineSnapshot snapshot() {
            return snapshotIn(phase);
        }

        @Override
        public EngineStep apply(GameAction action) {
            actions.add(action);
            if (action == GameAction.PAUSE) phase = EnginePhase.PAUSED;
            return next(null);
        }

        @Override
        public EngineStep tick() {
            ticks++;
            return next(new DropResult(1));
        }

        private EngineStep next(DropResult drop) {
            EngineStep step = queue.isEmpty() ? new EngineStep(snapshot(), drop, null, null) : queue.remove();
            phase = step.snapshot().phase();
            return step;
        }
    }
}
