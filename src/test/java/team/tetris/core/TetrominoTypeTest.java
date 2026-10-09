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
        // O 블록은 회전해도 차지하는 칸이 바뀌지 않아야 한다 (칸끼리는 자리를 바꾸며 돈다).
        Set<Position> base = Set.of(TetrominoType.O.cellsAt(0));

        for (int rotation = 1; rotation < TetrominoType.ROTATION_STATES; rotation++) {
            assertEquals(base, Set.of(TetrominoType.O.cellsAt(rotation)));
        }
    }

    @ParameterizedTest
    @EnumSource(TetrominoType.class)
    void sameIndexFollowsTheSameCellThroughEachClockwiseTurn(TetrominoType type) {
        // 블록의 한 칸에 붙은 아이템이 회전해도 같은 칸에 남으려면, 칸 0을 기준으로 한 각 칸의
        // 상대 위치가 회전마다 시계방향으로 90도 돌아야 한다: (dx, dy) -> (-dy, dx).
        for (int rotation = 0; rotation < TetrominoType.ROTATION_STATES; rotation++) {
            Position[] before = type.cellsAt(rotation);
            Position[] after = type.cellsAt(rotation + 1);
            for (int i = 0; i < before.length; i++) {
                int dx = before[i].x() - before[0].x();
                int dy = before[i].y() - before[0].y();
                assertEquals(new Position(-dy, dx),
                        new Position(after[i].x() - after[0].x(), after[i].y() - after[0].y()),
                        type + " cell " + i + " rotation " + rotation);
            }
        }
    }

    @ParameterizedTest
    @EnumSource(TetrominoType.class)
    void everyTetrominoIsAFourStateShapeOfFourCells(TetrominoType type) {
        Shape shape = type;

        assertEquals(TetrominoType.ROTATION_STATES, shape.rotationStates());
        assertEquals(4, shape.cellCount());
    }

    @Test
    void cellsAtReturnsACopyThatCannotMutateInternalState() {
        Position[] first = TetrominoType.T.cellsAt(0);
        first[0] = new Position(99, 99);

        Position[] second = TetrominoType.T.cellsAt(0);

        assertEquals(new Position(1, 0), second[0]);
    }
}
