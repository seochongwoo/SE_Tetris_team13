package team.tetris.core;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class PositionTest {

    @Test
    void translateMovesByGivenDelta() {
        Position start = new Position(3, 5);

        Position moved = start.translate(2, -1);

        assertEquals(new Position(5, 4), moved);
    }

    @Test
    void rotateAroundPivotRotatesClockwiseNinetyDegrees() {
        Position pivot = new Position(1, 1);
        Position point = new Position(2, 1); // pivot 기준 오른쪽 한 칸

        Position rotated = point.rotateAround(pivot);

        assertEquals(new Position(1, 2), rotated); // 시계방향 90도 => pivot 기준 아래 한 칸
    }

    @Test
    void fourSuccessiveRotationsReturnToOriginalPosition() {
        Position pivot = new Position(0, 0);
        Position point = new Position(3, 1);

        Position afterFourRotations = point
                .rotateAround(pivot)
                .rotateAround(pivot)
                .rotateAround(pivot)
                .rotateAround(pivot);

        assertEquals(point, afterFourRotations);
    }
}
