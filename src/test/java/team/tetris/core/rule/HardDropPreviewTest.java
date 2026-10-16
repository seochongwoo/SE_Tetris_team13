package team.tetris.core.rule;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Set;
import org.junit.jupiter.api.Test;
import team.tetris.core.*;
import team.tetris.core.item.*;

class HardDropPreviewTest {
    @Test void lineItemHighlightsOnlyExistingCellsInItsRowBeforeCompaction() {
        Cell[][] board = new Board(10, 20).snapshot();
        board[19][0] = Cell.occupiedBy(TetrominoType.J);
        board[18][0] = Cell.occupiedBy(TetrominoType.Z);
        ActivePiece active = active(Piece.of(TetrominoType.O).withItem(2, new LineClearItem()));
        var preview = verify(board, active, Set.of(new Position(0, 19)));
        var engine = engine(board, active);
        var step = engine.apply(GameAction.HARD_DROP);
        assertEquals(19, step.clearResult().clearedRows().getFirst().rowIndex());
        assertEquals(TetrominoType.Z, step.snapshot().board()[19][0].occupiedBy());
        assertEquals(step.lockResult().origin(), preview.landing().origin());
    }

    @Test void bombHighlightsOccupiedCellsOnlyWithinTheLandingBlast() {
        Cell[][] board = new Board(10, 20).snapshot();
        for (Position p : Set.of(new Position(3, 17), new Position(3, 18),
                new Position(4, 18), new Position(5, 18), new Position(4, 19))) {
            board[p.y()][p.x()] = Cell.occupiedBy(TetrominoType.J);
        }
        var active = new ActivePiece(new SoloBlock(new BombItem()).toItemPiece(null, null), 0, new Position(4, 0));
        verify(board, active, Set.of(new Position(3, 17), new Position(3, 18),
                new Position(4, 18), new Position(5, 18)));
        var after = engine(board, active).apply(GameAction.HARD_DROP).snapshot().board();
        assertTrue(after[18][4].isEmpty());
        assertEquals(TetrominoType.J, after[19][4].occupiedBy());
    }

    @Test void chainUsesConnectedSameTypeCellsAndIgnoresDiagonalAndDifferentTypes() {
        Cell[][] board = new Board(10, 20).snapshot();
        for (int y = 17; y < 20; y++) board[y][3] = Cell.occupiedBy(TetrominoType.O);
        board[16][2] = Cell.occupiedBy(TetrominoType.O);
        board[19][2] = Cell.occupiedBy(TetrominoType.J);
        var active = active(Piece.of(TetrominoType.O).withItem(0, new ChainClearItem()));
        verify(board, active, Set.of(new Position(3, 17), new Position(3, 18), new Position(3, 19)));
        var after = engine(board, active).apply(GameAction.HARD_DROP).snapshot().board();
        assertTrue(after[17][3].isEmpty());
        assertEquals(TetrominoType.O, after[16][2].occupiedBy());
        assertEquals(TetrominoType.J, after[19][2].occupiedBy());
    }

    @Test void weightHighlightsItsEntirePathIncludingOccupiedLandingCells() {
        Cell[][] board = new Board(10, 20).snapshot();
        var removed = Set.of(new Position(3, 5), new Position(6, 8), new Position(4, 19));
        for (Position p : removed) board[p.y()][p.x()] = Cell.occupiedBy(TetrominoType.J);
        board[19][2] = Cell.occupiedBy(TetrominoType.Z);
        var active = active(new WeightBlock().toItemPiece(null, null));
        var preview = verify(board, active, removed);
        assertEquals(new Position(3, 18), preview.landing().origin());
        assertEquals(preview.landing(), DropRule.landingPiece(board, active));
        var after = engine(board, active).apply(GameAction.HARD_DROP).snapshot().board();
        assertTrue(after[5][3].isEmpty());
        assertTrue(after[8][6].isEmpty());
        assertSame(WeightBlock.SHAPE, after[19][4].occupiedBy());
        assertEquals(TetrominoType.Z, after[19][2].occupiedBy());
    }

    @Test void ordinaryFullRowsAreHighlightedButScoreItemsAndUnconnectedChainsDeleteNothing() {
        Cell[][] board = new Board(10, 20).snapshot();
        board[19][0] = Cell.occupiedBy(TetrominoType.J);
        for (Item item : new Item[]{new DoubleScoreItem(), new ChainClearItem()}) {
            verify(board, active(Piece.of(TetrominoType.O).withItem(0, item)), Set.of());
        }
        Set<Position> full = new java.util.HashSet<>();
        for (int x = 0; x < 10; x++) {
            if (x >= 3 && x <= 6) continue;
            board[19][x] = Cell.occupiedBy(TetrominoType.J);
            full.add(new Position(x, 19));
        }
        verify(board, active(Piece.of(TetrominoType.I)), full);
    }

    @Test void previewDoesNotCallActualItemCallbacksEvenWhenAlreadyAtTheLandingPosition() {
        int[] calls = {0};
        Item item = new Item() {
            public char symbol() { return 'X'; }
            public void onLock(ItemContext context, Position position) { calls[0]++; }
        };
        var active = active(Piece.of(TetrominoType.O).withItem(0, item)).movedTo(new Position(3, 18));
        var engine = engine(new Board(10, 20).snapshot(), active);
        for (int i = 0; i < 10; i++) {
            assertTrue(HardDropPreview.project(engine.snapshot().board(), active).removedCells().isEmpty());
        }
        assertEquals(0, calls[0]);
        engine.apply(GameAction.HARD_DROP);
        assertEquals(1, calls[0]);
        assertNull(HardDropPreview.project(new Board(10, 20).snapshot(), null).landing());
    }

    private static HardDropPreview verify(Cell[][] cells, ActivePiece active, Set<Position> expected) {
        var engine = engine(cells, active);
        var before = engine.snapshot();
        var preview = HardDropPreview.project(cells, active);
        assertEquals(expected, preview.removedCells());
        assertEquals(preview, HardDropPreview.project(cells, active));
        assertArrayEquals(before.board(), cells);
        assertArrayEquals(before.board(), engine.snapshot().board());
        assertEquals(before.activePiece(), engine.snapshot().activePiece());
        assertEquals(before.nextPiece(), engine.snapshot().nextPiece());
        assertThrows(UnsupportedOperationException.class, () -> preview.removedCells().clear());
        assertEquals(engine.apply(GameAction.HARD_DROP).lockResult().origin(), preview.landing().origin());
        return preview;
    }

    private static ActivePiece active(Piece piece) {
        return new ActivePiece(piece, 0, new Position(3, 0));
    }

    private static PlayerEngine engine(Cell[][] board, ActivePiece active) {
        return new PlayerEngine(board, active, new PieceSource() {
            public Piece next() { return peek(); }
            public Piece peek() { return Piece.of(TetrominoType.T); }
        });
    }
}
