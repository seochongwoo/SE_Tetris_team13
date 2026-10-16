package team.tetris.core.rule;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import team.tetris.core.*;
import team.tetris.core.item.Item;
import team.tetris.core.item.ItemContext;
import team.tetris.core.item.BombItem;
import team.tetris.core.item.SoloBlock;
import team.tetris.core.item.WeightBlock;

class DropRuleTest {
    @ParameterizedTest @EnumSource(TetrominoType.class)
    void everyRotationAndHorizontalPositionMatchesActualHardDrop(TetrominoType type) {
        for (int rotation = 0; rotation < 4; rotation++) {
            for (int x = 0; x <= 6; x++) {
                Cell[][] board = emptyBoard();
                board[14][4] = Cell.occupiedBy(TetrominoType.J);
                board[18][2] = Cell.occupiedBy(TetrominoType.J);
                board[19][7] = Cell.occupiedBy(TetrominoType.J);
                ActivePiece initial = new ActivePiece(type, rotation, new Position(x, 0));
                PlayerEngine engine = new PlayerEngine(board, initial, source(Piece.of(type)));

                ActivePiece ghost = DropRule.landingPiece(board, initial);
                var drop = engine.apply(GameAction.HARD_DROP);

                assertEquals(drop.lockResult().origin(), ghost.origin());
                assertEquals(drop.lockResult().rotation(), ghost.rotation());
                assertEquals(drop.dropResult().cellsDropped(), ghost.origin().y() - initial.origin().y());
            }
        }
    }

    @Test void bombStopsAboveTheStackWithoutRemovingCellsOrDrawingPieces() {
        Cell[][] board = emptyBoard();
        for (int y = 5; y < 20; y++) board[y][4] = Cell.occupiedBy(TetrominoType.O);
        ActivePiece bomb = new ActivePiece(new SoloBlock(new BombItem()).toItemPiece(null, null),
                0, new Position(4, 0));
        PlayerEngine engine = new PlayerEngine(board, bomb, source(Piece.of(TetrominoType.T)));
        EngineSnapshot before = engine.snapshot();

        ActivePiece ghost = DropRule.landingPiece(before.board(), before.activePiece());

        assertEquals(new Position(4, 4), ghost.origin());
        assertArrayEquals(board, before.board());
        assertArrayEquals(board, engine.snapshot().board());
        assertEquals(before.activePiece(), engine.snapshot().activePiece());
        assertEquals(before.nextPiece(), engine.snapshot().nextPiece());
        assertEquals(ghost.origin(), engine.apply(GameAction.HARD_DROP).lockResult().origin());
    }

    @Test void weightProjectionCrushesTheStackWithoutChangingTheBoardOrSource() {
        Cell[][] board = emptyBoard();
        board[10][3] = Cell.occupiedBy(TetrominoType.J);
        board[19][4] = Cell.occupiedBy(TetrominoType.O);
        board[19][8] = Cell.occupiedBy(TetrominoType.Z);
        ActivePiece initial = new ActivePiece(new WeightBlock().toItemPiece(null, null),
                0, new Position(3, 0));
        int[] draws = {0};
        PieceSource source = new PieceSource() {
            public Piece peek() { return Piece.of(TetrominoType.T); }
            public Piece next() { draws[0]++; return peek(); }
        };
        PlayerEngine engine = new PlayerEngine(board, initial, source);
        Cell[][] original = engine.snapshot().board();

        ActivePiece ghost = DropRule.landingPiece(board, initial);

        assertEquals(new Position(3, 18), ghost.origin());
        assertArrayEquals(original, board);
        assertArrayEquals(original, engine.snapshot().board());
        assertEquals(initial, engine.snapshot().activePiece());
        assertEquals(0, draws[0]);
        var dropped = engine.apply(GameAction.HARD_DROP);
        assertEquals(ghost.origin(), dropped.lockResult().origin());
        assertEquals(18, dropped.dropResult().cellsDropped());
        assertTrue(dropped.snapshot().board()[10][3].isEmpty());
        assertEquals(TetrominoType.Z, dropped.snapshot().board()[19][8].occupiedBy());
    }

    @Test void projectionDoesNotActivateAttachedItems() {
        int[] activations = {0};
        Item item = new Item() {
            @Override public char symbol() { return 'X'; }
            @Override public void onLock(ItemContext context, Position position) { activations[0]++; }
        };
        ActivePiece active = new ActivePiece(Piece.of(TetrominoType.T).withItem(0, item), 1, new Position(3, 0));
        PlayerEngine engine = new PlayerEngine(emptyBoard(), active, source(Piece.of(TetrominoType.O)));
        for (int i = 0; i < 10; i++) DropRule.landingPiece(engine.snapshot().board(), active);
        assertEquals(0, activations[0]);
        engine.apply(GameAction.HARD_DROP);
        assertEquals(1, activations[0]);
    }

    private static Cell[][] emptyBoard() {
        Cell[][] board = new Cell[20][10];
        for (Cell[] row : board) Arrays.fill(row, Cell.EMPTY);
        return board;
    }

    private static PieceSource source(Piece piece) {
        return new PieceSource() {
            @Override public Piece peek() { return piece; }
            @Override public Piece next() { return piece; }
        };
    }
}
