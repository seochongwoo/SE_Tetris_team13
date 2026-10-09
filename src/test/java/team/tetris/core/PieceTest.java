package team.tetris.core;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import team.tetris.core.item.Item;

class PieceTest {

    private final Item item = TestItems.marker('M');

    @Test
    void plainPieceHasNoItems() {
        Piece piece = Piece.of(TetrominoType.T);

        assertFalse(piece.hasItems());
        assertNull(piece.itemAt(0));
        assertEquals(TetrominoType.T, piece.shape());
    }

    @Test
    void withItemPutsTheItemOnOneCellAndLeavesTheOriginalUntouched() {
        Piece plain = Piece.of(TetrominoType.I);

        Piece withItem = plain.withItem(2, item);

        assertTrue(withItem.hasItems());
        assertSame(item, withItem.itemAt(2));
        assertNull(withItem.itemAt(1));
        assertFalse(plain.hasItems());
    }

    @Test
    void itemIndexMustPointAtOneOfTheShapesCells() {
        Piece piece = Piece.of(TetrominoType.O);

        assertThrows(IllegalArgumentException.class, () -> piece.withItem(4, item));
        assertThrows(IllegalArgumentException.class, () -> piece.withItem(-1, item));
        assertThrows(NullPointerException.class, () -> piece.withItem(0, null));
    }

    @Test
    void itemsAreCopiedSoLaterChangesToTheSourceMapDoNotLeakIn() {
        Map<Integer, Item> items = new HashMap<>();
        items.put(0, item);
        Piece piece = new Piece(TetrominoType.T, items);

        items.put(1, item);

        assertNull(piece.itemAt(1));
    }

    @Test
    void activePieceCellsAreTheShapeOffsetsMovedToTheOrigin() {
        ActivePiece piece = new ActivePiece(TetrominoType.T, 0, new Position(3, 5));

        assertArrayEquals(new Position[] {
                new Position(4, 5), new Position(3, 6), new Position(4, 6), new Position(5, 6)},
                piece.cells());
    }

    @Test
    void itemStaysOnTheSameCellWhenThePieceRotates() {
        // T(회전0)의 0번 칸은 위로 튀어나온 칸 (1,0). 시계방향으로 돌면 오른쪽으로 튀어나온 칸 (2,1)이 된다.
        ActivePiece spawned = new ActivePiece(Piece.of(TetrominoType.T).withItem(0, item), 0, new Position(0, 0));

        ActivePiece turned = spawned.rotatedTo(1, spawned.origin());

        assertSame(item, turned.itemAt(0));
        assertEquals(new Position(2, 1), turned.cells()[0]);
    }

    @Test
    void movingKeepsThePieceAndItsItems() {
        ActivePiece piece = new ActivePiece(Piece.of(TetrominoType.L).withItem(3, item), 1, new Position(0, 0));

        ActivePiece moved = piece.movedTo(new Position(2, 4));

        assertEquals(piece.piece(), moved.piece());
        assertEquals(1, moved.rotation());
        assertEquals(new Position(2, 4), moved.origin());
    }
}
