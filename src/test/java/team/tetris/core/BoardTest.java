package team.tetris.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;
import team.tetris.core.result.ClearResult;
import team.tetris.core.result.ClearedRow;

class BoardTest {

    @Test
    void constructorRejectsNonPositiveDimensions() {
        assertThrows(IllegalArgumentException.class, () -> new Board(0, 5));
        assertThrows(IllegalArgumentException.class, () -> new Board(5, 0));
        assertThrows(IllegalArgumentException.class, () -> new Board(-1, 5));
    }

    @Test
    void newBoardIsCompletelyEmpty() {
        Board board = new Board(4, 4);

        for (Cell[] row : board.snapshot()) {
            for (Cell cell : row) {
                assertTrue(cell.isEmpty());
            }
        }
    }

    @Test
    void canPlaceIsTrueWithinEmptyBoardBounds() {
        Board board = new Board(4, 4);

        assertTrue(board.canPlace(TetrominoType.O, 0, new Position(1, 0)));
    }

    @Test
    void canPlaceIsFalseWhenPastLeftWall() {
        Board board = new Board(4, 4);

        // I(rotation 0) 상대좌표: (0,1)(1,1)(2,1)(3,1) -> origin(-1,0)이면 x=-1까지 나감
        assertFalse(board.canPlace(TetrominoType.I, 0, new Position(-1, 0)));
    }

    @Test
    void canPlaceIsFalseWhenPastRightWall() {
        Board board = new Board(4, 4);

        // origin(1,0)이면 x=1..4까지 나가서 width(4)를 벗어남
        assertFalse(board.canPlace(TetrominoType.I, 0, new Position(1, 0)));
    }

    @Test
    void canPlaceIsFalseWhenPastFloor() {
        Board board = new Board(4, 4);

        // O(rotation 0) 상대좌표: (1,0)(2,0)(1,1)(2,1) -> origin(1,3)이면 y=4까지 나가서 height(4)를 벗어남
        assertFalse(board.canPlace(TetrominoType.O, 0, new Position(1, 3)));
    }

    @Test
    void canPlaceAllowsCellsAboveTheVisibleBoard() {
        Board board = new Board(4, 4);

        // origin(1,-1)이면 O가 y=-1(보드 밖 위쪽)과 y=0에 걸침 - 스폰 여유 공간이라 허용되어야 함
        assertTrue(board.canPlace(TetrominoType.O, 0, new Position(1, -1)));
    }

    @Test
    void canPlaceIsFalseWhenOverlappingAnExistingBlock() {
        Board board = new Board(4, 4);
        board.place(TetrominoType.T, 0, new Position(0, 0));

        // T가 이미 row0 col1, row1 col0/1/2를 채운 상태에서 겹치는 O 배치 시도
        assertFalse(board.canPlace(TetrominoType.O, 0, new Position(0, 0)));
    }

    @Test
    void placeThrowsWhenPlacementIsInvalid() {
        Board board = new Board(4, 4);

        assertThrows(IllegalStateException.class, () -> board.place(TetrominoType.I, 0, new Position(1, 0)));
    }

    @Test
    void placeFillsExactlyTheOccupiedCellsWithTheGivenType() {
        Board board = new Board(4, 4);

        board.place(TetrominoType.T, 0, new Position(0, 0));
        Cell[][] snapshot = board.snapshot();

        assertEquals(Cell.occupiedBy(TetrominoType.T), snapshot[0][1]);
        assertEquals(Cell.occupiedBy(TetrominoType.T), snapshot[1][0]);
        assertEquals(Cell.occupiedBy(TetrominoType.T), snapshot[1][1]);
        assertEquals(Cell.occupiedBy(TetrominoType.T), snapshot[1][2]);
        // T가 차지하지 않은 칸은 그대로 비어있어야 함
        assertTrue(snapshot[0][0].isEmpty());
        assertTrue(snapshot[0][2].isEmpty());
        assertTrue(snapshot[0][3].isEmpty());
        assertTrue(snapshot[1][3].isEmpty());
    }

    @Test
    void placeIgnoresPartsAboveTheVisibleBoard() {
        Board board = new Board(4, 4);

        // O(rotation 0) 상대좌표는 (1,0)(2,0)(1,1)(2,1) -> origin(1,-1)이면 절대좌표로
        // (2,-1)(3,-1)(2,0)(3,0)이 된다. y=-1에 걸친 부분은 기록되지 않고, row0(col2,col3)만 채워짐.
        board.place(TetrominoType.O, 0, new Position(1, -1));
        Cell[][] snapshot = board.snapshot();

        assertEquals(Cell.occupiedBy(TetrominoType.O), snapshot[0][2]);
        assertEquals(Cell.occupiedBy(TetrominoType.O), snapshot[0][3]);
        assertTrue(snapshot[0][0].isEmpty());
        assertTrue(snapshot[0][1].isEmpty());
    }

    @Test
    void isRowFullOnlyWhenEveryColumnIsOccupied() {
        Board board = new Board(4, 4);
        board.place(TetrominoType.I, 0, new Position(0, 2)); // row3 전체를 채움 (offset y=1 -> row 2+1=3)

        assertTrue(board.isRowFull(3));
        assertFalse(board.isRowFull(0));
        assertFalse(board.isRowFull(2));
    }

    @Test
    void clearFullLinesReturnsNoneWhenNothingIsFull() {
        Board board = new Board(4, 4);
        board.place(TetrominoType.T, 0, new Position(0, 0));

        ClearResult result = board.clearFullLines();

        assertEquals(ClearResult.NONE, result);
        assertTrue(result.isEmpty());
        assertEquals(0, result.lineCount());
    }

    @Test
    void clearFullLinesRemovesOneFullRowAndShiftsRowsAboveDown() {
        Board board = new Board(4, 4);
        // 맨 위 두 행에 걸치는 마커 (완전히 채우지는 않음): row0 col1, row1 col0/1/2
        board.place(TetrominoType.T, 0, new Position(0, 0));
        // 맨 아래 행(row3)을 완전히 채움
        board.place(TetrominoType.I, 0, new Position(0, 2));

        ClearResult result = board.clearFullLines();

        assertEquals(1, result.lineCount());
        assertEquals(List.of(new ClearedRow(3)), result.clearedRows());

        Cell[][] snapshot = board.snapshot();
        // row0은 새로 채워진 빈 행
        for (Cell cell : snapshot[0]) {
            assertTrue(cell.isEmpty());
        }
        // 원래 row0(마커 윗부분)이 한 칸 내려와 row1이 됨
        assertTrue(snapshot[1][0].isEmpty());
        assertEquals(Cell.occupiedBy(TetrominoType.T), snapshot[1][1]);
        assertTrue(snapshot[1][2].isEmpty());
        assertTrue(snapshot[1][3].isEmpty());
        // 원래 row1(마커 아랫부분)이 한 칸 내려와 row2가 됨
        assertEquals(Cell.occupiedBy(TetrominoType.T), snapshot[2][0]);
        assertEquals(Cell.occupiedBy(TetrominoType.T), snapshot[2][1]);
        assertEquals(Cell.occupiedBy(TetrominoType.T), snapshot[2][2]);
        assertTrue(snapshot[2][3].isEmpty());
        // 원래 비어있던 row2가 내려와 row3이 됨 - 완전히 비어있어야 함
        for (Cell cell : snapshot[3]) {
            assertTrue(cell.isEmpty());
        }
    }

    @Test
    void clearFullLinesRemovesMultipleSimultaneousRowsAndShiftsRemainderDown() {
        Board board = new Board(4, 5);
        // 맨 위(row0,row1)에 걸치는 마커: row0 col1, row1 col0/1/2
        board.place(TetrominoType.T, 0, new Position(0, 0));
        // row2, row3을 O 두 개로 완전히 채움 (O 상대좌표는 x=1,2 기준이라 origin.x=-1과 1로 좌우를 채움)
        board.place(TetrominoType.O, 0, new Position(-1, 2));
        board.place(TetrominoType.O, 0, new Position(1, 2));
        // row4는 비어있는 채로 둠

        ClearResult result = board.clearFullLines();

        assertEquals(2, result.lineCount());
        assertEquals(List.of(new ClearedRow(2), new ClearedRow(3)), result.clearedRows());

        Cell[][] snapshot = board.snapshot();
        for (Cell cell : snapshot[0]) {
            assertTrue(cell.isEmpty());
        }
        for (Cell cell : snapshot[1]) {
            assertTrue(cell.isEmpty());
        }
        // 마커가 두 칸 내려와 row2, row3이 됨
        assertTrue(snapshot[2][0].isEmpty());
        assertEquals(Cell.occupiedBy(TetrominoType.T), snapshot[2][1]);
        assertEquals(Cell.occupiedBy(TetrominoType.T), snapshot[3][0]);
        assertEquals(Cell.occupiedBy(TetrominoType.T), snapshot[3][1]);
        assertEquals(Cell.occupiedBy(TetrominoType.T), snapshot[3][2]);
        // 원래 비어있던 row4는 그대로 비어있음
        for (Cell cell : snapshot[4]) {
            assertTrue(cell.isEmpty());
        }
    }

    @Test
    void snapshotIsADefensiveCopyThatCannotMutateBoardState() {
        Board board = new Board(3, 3);

        Cell[][] first = board.snapshot();
        first[0][0] = Cell.occupiedBy(TetrominoType.Z);

        Cell[][] second = board.snapshot();

        assertTrue(second[0][0].isEmpty());
    }
}
