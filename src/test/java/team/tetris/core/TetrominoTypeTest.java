package team.tetris.core;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class TetrominoTypeTest {

    @ParameterizedTest
    @EnumSource(TetrominoType.class)
    void everyRotationStateOccupiesExactlyFourDistinctCells(TetrominoType type) {
        for (int rotation = 0; rotation < TetrominoType.ROTATION_STATES; rotation++) {
            Position[] cells = type.cellsAt(rotation);

            assertEquals(4, cells.length, type + " rotation " + rotation + " must have 4 cells");
            assertEquals(4, Set.of(cells).size(), type + " rotation " + rotation + " must not repeat a cell");
        }
    }

    @ParameterizedTest
    @EnumSource(TetrominoType.class)
    void rotationIndexWrapsEveryFourStates(TetrominoType type) {
        assertArrayEquals(type.cellsAt(0), type.cellsAt(4));
        assertArrayEquals(type.cellsAt(1), type.cellsAt(5));
        assertArrayEquals(type.cellsAt(0), type.cellsAt(-4));
        assertArrayEquals(type.cellsAt(3), type.cellsAt(-1));
    }

    @Test
    void oPieceShapeIsIdenticalAcrossAllRotations() {
        // O 블록은 회전해도 모양이 바뀌지 않아야 한다.
        Position[] base = TetrominoType.O.cellsAt(0);

        for (int rotation = 1; rotation < TetrominoType.ROTATION_STATES; rotation++) {
            assertArrayEquals(base, TetrominoType.O.cellsAt(rotation));
        }
    }

    @Test
    void cellsAtReturnsACopyThatCannotMutateInternalState() {
        Position[] first = TetrominoType.T.cellsAt(0);
        first[0] = new Position(99, 99);

        Position[] second = TetrominoType.T.cellsAt(0);

        assertEquals(new Position(1, 0), second[0]);
    }
}
