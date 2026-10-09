package team.tetris.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import team.tetris.core.item.Item;

class CellTest {

    static final Item MARK = TestItems.marker('M');

    @Test
    void emptyConstantIsEmptyAndHasNoOccupantOrItem() {
        assertTrue(Cell.EMPTY.isEmpty());
        assertNull(Cell.EMPTY.occupiedBy());
        assertFalse(Cell.EMPTY.hasItem());
    }

    @Test
    void occupiedByStoresTheGivenShapeWithoutAnItem() {
        Cell cell = Cell.occupiedBy(TetrominoType.T);

        assertFalse(cell.isEmpty());
        assertEquals(TetrominoType.T, cell.occupiedBy());
        assertFalse(cell.hasItem());
    }

    @Test
    void occupiedCellWithoutShapeIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> Cell.occupiedBy(null));
        assertThrows(IllegalArgumentException.class, () -> Cell.withItem(null, MARK));
    }

    @Test
    void emptyCellCannotHoldAnItem() {
        assertThrows(IllegalArgumentException.class, () -> new Cell(null, MARK));
    }

    @Test
    void itemCanBeRemovedLeavingTheBlock() {
        Cell cell = Cell.withItem(TetrominoType.I, MARK);
        assertTrue(cell.hasItem());

        assertEquals(Cell.occupiedBy(TetrominoType.I), cell.withoutItem());
        Cell plain = Cell.occupiedBy(TetrominoType.I);
        assertSame(plain, plain.withoutItem());
    }
}
