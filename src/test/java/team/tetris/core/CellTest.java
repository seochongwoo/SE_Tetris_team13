package team.tetris.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class CellTest {

    @Test
    void emptyConstantIsEmptyAndHasNoOccupant() {
        assertTrue(Cell.EMPTY.isEmpty());
        assertEquals(CellKind.EMPTY, Cell.EMPTY.kind());
        assertNull(Cell.EMPTY.occupiedBy());
    }

    @Test
    void occupiedByStoresTheGivenTetrominoType() {
        Cell cell = Cell.occupiedBy(TetrominoType.T);

        assertFalse(cell.isEmpty());
        assertEquals(CellKind.OCCUPIED, cell.kind());
        assertEquals(TetrominoType.T, cell.occupiedBy());
    }

    @Test
    void occupiedCellWithoutTypeIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> new Cell(CellKind.OCCUPIED, null));
    }

    @Test
    void emptyCellWithTypeIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> new Cell(CellKind.EMPTY, TetrominoType.I));
    }
}
