package team.tetris.core.item;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import team.tetris.core.*;
import team.tetris.core.result.EngineStep;
import team.tetris.core.rule.ItemPieceSource;
import team.tetris.core.rule.PieceSource;

class RequiredItemsTest {
    private static final Piece WEIGHT = new WeightBlock().toItemPiece(null, null);

    private static final class Sequence implements PieceSource {
        private final Deque<Piece> pieces;
        private int lines;

        Sequence(Piece... pieces) {
            this.pieces = new ArrayDeque<>(List.of(pieces));
        }

        @Override
        public Piece next() {
            return pieces.isEmpty() ? Piece.of(TetrominoType.O) : pieces.remove();
        }

        @Override
        public Piece peek() {
            return pieces.isEmpty() ? Piece.of(TetrominoType.O) : pieces.peek();
        }

        @Override
        public void onLinesCleared(int count) {
            lines += count;
        }
    }

    @Test
    void lineItemCanAttachToEveryCellAndFollowsThatCellThroughRotation() {
        Random random = new Random(7);
        AttachedItem kind = new AttachedItem(new LineClearItem());
        Set<Integer> indices = new HashSet<>();
        for (int i = 0; i < 100; i++) {
            Piece piece = kind.toItemPiece(Piece.of(TetrominoType.I), random);
            int index = piece.items().keySet().iterator().next();
            indices.add(index);
            PlayerEngine engine = new PlayerEngine(10, 20, new Sequence(piece));
            engine.apply(GameAction.ROTATE_CW);
            EngineStep step = engine.apply(GameAction.HARD_DROP);
            Position expected = new ActivePiece(piece, step.lockResult().rotation(),
                    step.lockResult().origin()).cells()[index];
            assertEquals(expected, step.itemActivations().getFirst().position());
            assertEquals(expected.y(), step.clearResult().clearedRows().getFirst().rowIndex());
            assertEquals(1, step.clearResult().lineCount());
        }
        assertEquals(Set.of(0, 1, 2, 3), indices);
    }

    @Test
    void lineItemClearsAnIncompleteRowAndShiftsTheRowsAbove() {
        Sequence source = new Sequence(Piece.of(TetrominoType.O).withItem(2, new LineClearItem()));
        PlayerEngine engine = new PlayerEngine(6, 8, source);
        EngineStep step = engine.apply(GameAction.HARD_DROP);
        assertEquals(1, step.clearResult().lineCount());
        assertEquals(1, source.lines);
        assertEquals(7, step.clearResult().clearedRows().getFirst().rowIndex());
        assertEquals(2, filled(step.snapshot().board()));
        assertEquals(TetrominoType.O, step.snapshot().board()[7][2].occupiedBy());
        assertTrue(step.snapshot().board()[6][2].isEmpty());
    }

    @Test
    void itemRowAndFullRowAreCombinedAndAnOverlappingRowIsCountedOnce() {
        // 폭 4: 아래의 I가 꽉 차고, 위 한 칸의 L은 다른 줄을 요청한다.
        Shape ledge = new Shape() {
            public Position[] cellsAt(int rotation) {
                return new Position[] {new Position(0, 0), new Position(0, 1),
                    new Position(1, 1), new Position(2, 1), new Position(3, 1)};
            }
            public int rotationStates() { return 1; }
        };
        PlayerEngine combined = new PlayerEngine(4, 8,
                new Sequence(Piece.of(ledge).withItem(0, new LineClearItem())));
        assertEquals(2, combined.apply(GameAction.HARD_DROP).clearResult().lineCount());

        PlayerEngine overlap = new PlayerEngine(4, 8,
                new Sequence(Piece.of(TetrominoType.I).withItem(0, new LineClearItem())));
        assertEquals(1, overlap.apply(GameAction.HARD_DROP).clearResult().lineCount());
    }

    @Test
    void lineItemContributesToTheNextTenLineItemThreshold() {
        Piece line = Piece.of(TetrominoType.O).withItem(2, new LineClearItem());
        ItemPieceSource source = new ItemPieceSource(new Sequence(line),
                List.of(new WeightBlock()), new Random(1));
        PlayerEngine engine = new PlayerEngine(6, 8, source);
        source.onLinesCleared(9);
        engine.apply(GameAction.HARD_DROP);
        assertSame(WeightBlock.SHAPE, engine.snapshot().nextPiece().shape());
    }

    @Test
    void weightHasSixCellsFourColumnsAndCannotRotateOrCrossTheWalls() {
        assertEquals(6, WEIGHT.shape().cellCount());
        Position[] offsets = WEIGHT.shape().cellsAt(0);
        offsets[0] = new Position(99, 99);
        assertEquals(new Position(1, 0), WEIGHT.shape().cellsAt(0)[0]);
        PlayerEngine engine = new PlayerEngine(8, 10, new Sequence(WEIGHT));
        for (int i = 0; i < 10; i++) engine.apply(GameAction.MOVE_LEFT);
        assertEquals(0, engine.snapshot().activePiece().origin().x());
        engine.apply(GameAction.ROTATE_CW);
        engine.apply(GameAction.ROTATE_CCW);
        assertEquals(0, engine.snapshot().activePiece().rotation());
        for (int i = 0; i < 10; i++) engine.apply(GameAction.MOVE_RIGHT);
        assertEquals(4, engine.snapshot().activePiece().origin().x());
    }

    @Test
    void contactLocksLateralMovementImmediatelyAndSoftDropCrushesOneRowAtATime() {
        PlayerEngine engine = stackedWeight();
        // O는 x=3~4, y=8~9. 무게추의 바닥이 y=7이면 처음 접촉한다.
        for (int i = 0; i < 6; i++) engine.tick();
        Position contact = engine.snapshot().activePiece().origin();
        assertEquals(new Position(2, 6), contact);
        engine.apply(GameAction.MOVE_LEFT);
        engine.apply(GameAction.MOVE_RIGHT);
        assertEquals(contact, engine.snapshot().activePiece().origin());
        assertEquals(4, filled(engine.snapshot().board()));

        EngineStep step = engine.apply(GameAction.SOFT_DROP);
        assertNull(step.lockResult());
        assertEquals(1, step.dropResult().cellsDropped());
        assertEquals(2, filled(step.snapshot().board()));
        assertEquals(TetrominoType.O, step.snapshot().board()[9][3].occupiedBy());
        engine.tick();
        assertEquals(0, filled(engine.snapshot().board()));
        Position floor = engine.snapshot().activePiece().origin();
        engine.apply(GameAction.MOVE_LEFT);
        assertEquals(floor, engine.snapshot().activePiece().origin());

        EngineStep locked = engine.tick();
        assertNotNull(locked.lockResult());
        assertEquals(6, filled(locked.snapshot().board()));
        assertEquals(1, locked.itemActivations().size());
        for (Cell[] row : locked.snapshot().board()) {
            for (Cell cell : row) assertFalse(cell.hasItem());
        }
        Position spawned = engine.snapshot().activePiece().origin();
        engine.apply(GameAction.MOVE_LEFT);
        assertEquals(spawned.translate(-1, 0), engine.snapshot().activePiece().origin());
    }

    @Test
    void hardDropAndRepeatedGravityProduceTheSameBoardAndDistance() {
        PlayerEngine hard = stackedWeight();
        PlayerEngine gravity = stackedWeight();
        EngineStep hardStep = hard.apply(GameAction.HARD_DROP);
        int distance = 0;
        EngineStep step;
        do {
            step = gravity.tick();
            if (step.dropResult() != null) distance += step.dropResult().cellsDropped();
        } while (step.lockResult() == null);
        assertEquals(8, distance);
        assertEquals(distance, hardStep.dropResult().cellsDropped());
        assertArrayEquals(step.snapshot().board(), hardStep.snapshot().board());
        assertEquals(0, hardStep.clearResult().lineCount());
    }

    @Test
    void contactAtSpawnLocksMovementAndPauseDoesNotCrushCells() {
        PlayerEngine engine = new PlayerEngine(8, 4,
                new Sequence(Piece.of(TetrominoType.O), WEIGHT));
        engine.apply(GameAction.HARD_DROP);
        Position origin = engine.snapshot().activePiece().origin();
        engine.apply(GameAction.MOVE_RIGHT);
        assertEquals(origin, engine.snapshot().activePiece().origin());
        engine.apply(GameAction.PAUSE);
        engine.tick();
        engine.apply(GameAction.HARD_DROP);
        assertEquals(4, filled(engine.snapshot().board()));
        assertEquals(origin, engine.snapshot().activePiece().origin());
        engine.apply(GameAction.RESUME);
        engine.apply(GameAction.HARD_DROP);
        assertEquals(6, filled(engine.snapshot().board()));
    }

    @Test
    void weightOnlyCrushesItsFourColumnsAndNeverClearsAboveItsPath() {
        PlayerEngine engine = new PlayerEngine(8, 10,
                new Sequence(Piece.of(TetrominoType.O), WEIGHT));
        engine.apply(GameAction.MOVE_LEFT); // O는 x=2~3
        engine.apply(GameAction.HARD_DROP);
        engine.apply(GameAction.MOVE_RIGHT); // W는 x=3~6
        engine.apply(GameAction.HARD_DROP);
        Cell[][] board = engine.snapshot().board();
        assertEquals(TetrominoType.O, board[8][2].occupiedBy());
        assertEquals(TetrominoType.O, board[9][2].occupiedBy());
        assertTrue(board[8][3].isEmpty());
        assertEquals(8, filled(board));
    }

    @Test
    void weightDoesNotCrushOccupiedSpawnCellsToAvoidGameOver() {
        PlayerEngine engine = new PlayerEngine(8, 3,
                new Sequence(Piece.of(TetrominoType.O), WEIGHT));
        engine.apply(GameAction.HARD_DROP);
        assertEquals(EnginePhase.GAME_OVER, engine.snapshot().phase());
        assertEquals(4, filled(engine.snapshot().board()));
    }

    @Test
    void movingOverASuspendedCellLocksTheWeightEvenAfterItCrushesThatCell() {
        Shape suspended = new Shape() {
            public Position[] cellsAt(int rotation) {
                return new Position[] {new Position(0, 0), new Position(6, 6)};
            }
            public int rotationStates() { return 1; }
        };
        PlayerEngine engine = new PlayerEngine(8, 10, new Sequence(Piece.of(suspended), WEIGHT));
        engine.apply(GameAction.HARD_DROP); // (0,3)과 무게추 경로 밖 (6,9)
        engine.tick();
        engine.apply(GameAction.MOVE_LEFT);
        engine.apply(GameAction.MOVE_LEFT); // x=0으로 이동하는 순간 (0,3)에 접촉
        assertEquals(new Position(0, 1), engine.snapshot().activePiece().origin());
        engine.apply(GameAction.MOVE_RIGHT);
        assertEquals(new Position(0, 1), engine.snapshot().activePiece().origin());
        engine.tick(); // (0,3)을 지워 아래가 다시 비었지만 이동 잠금은 유지
        engine.apply(GameAction.MOVE_RIGHT);
        assertEquals(new Position(0, 2), engine.snapshot().activePiece().origin());
        assertEquals(1, filled(engine.snapshot().board()));
    }

    @Test
    void touchingAnEmptyFloorLocksTheWeightBeforeTheNextTick() {
        PlayerEngine engine = new PlayerEngine(8, 3, new Sequence(WEIGHT));
        engine.tick();
        Position floor = engine.snapshot().activePiece().origin();
        engine.apply(GameAction.MOVE_LEFT);
        engine.apply(GameAction.MOVE_RIGHT);
        assertEquals(floor, engine.snapshot().activePiece().origin());
        assertNotNull(engine.tick().lockResult());
    }

    @ParameterizedTest
    @ValueSource(ints = {2, 18})
    void initialWeightContactLocksMovementBeforeTheFirstTick(int row) {
        Cell[][] board = new Board(10, 20).snapshot();
        if (row == 2) board[4][3] = Cell.occupiedBy(TetrominoType.J);
        ActivePiece initial = new ActivePiece(WEIGHT, 0, new Position(3, row));
        PlayerEngine engine = new PlayerEngine(board, initial, new Sequence());

        engine.apply(GameAction.MOVE_LEFT);
        assertEquals(initial, engine.snapshot().activePiece());
        engine.apply(GameAction.MOVE_RIGHT);
        assertEquals(initial, engine.snapshot().activePiece());
        assertArrayEquals(board, engine.snapshot().board());

        if (row == 2) {
            engine.tick(); // 접촉한 칸을 지워도 이동 잠금은 유지된다.
            assertTrue(engine.snapshot().board()[4][3].isEmpty());
            ActivePiece descended = engine.snapshot().activePiece();
            engine.apply(GameAction.MOVE_RIGHT);
            assertEquals(descended, engine.snapshot().activePiece());
        }
    }

    private static PlayerEngine stackedWeight() {
        PlayerEngine engine = new PlayerEngine(8, 10,
                new Sequence(Piece.of(TetrominoType.O), WEIGHT));
        engine.apply(GameAction.HARD_DROP);
        return engine;
    }

    private static int filled(Cell[][] board) {
        int count = 0;
        for (Cell[] row : board) for (Cell cell : row) if (!cell.isEmpty()) count++;
        return count;
    }
}
