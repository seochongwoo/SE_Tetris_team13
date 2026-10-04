package team.tetris.core.rule;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import team.tetris.core.Board;
import team.tetris.core.Position;
import team.tetris.core.TetrominoType;

class RotationSystemTest {

    private final RotationSystem rotationSystem = new RotationSystem();

    @Test
    void rotatesInPlaceWhenThereIsOpenSpace() {
        Board board = new Board(6, 6);
        Position origin = new Position(2, 2);

        RotationResult result = rotationSystem.tryRotate(board, TetrominoType.T, 0, origin, true);

        assertTrue(result.rotated());
        assertEquals(1, result.rotation());
        assertEquals(origin, result.origin()); // 트인 공간이라 밀려날 필요 없이 제자리에서 성공
    }

    @Test
    void counterclockwiseDecrementsRotationAndWrapsToThree() {
        Board board = new Board(6, 6);
        Position origin = new Position(2, 2);

        RotationResult result = rotationSystem.tryRotate(board, TetrominoType.T, 0, origin, false);

        assertTrue(result.rotated());
        assertEquals(3, result.rotation());
    }

    @Test
    void kicksAwayFromTheLeftWallWhenInPlaceRotationWouldGoOutOfBounds() {
        Board board = new Board(6, 6);
        // I(rotation 1, 세로)의 상대좌표는 x=1 고정이라 origin=(-1,0)이면 절대 x=0 -> 왼쪽 벽에 붙어있음
        Position origin = new Position(-1, 0);

        RotationResult result = rotationSystem.tryRotate(board, TetrominoType.I, 1, origin, true);

        // 제자리(rotation 2, 가로)로 그냥 회전하면 x=-1까지 나가서 실패 -> 오른쪽으로 밀려나야 함
        assertTrue(result.rotated());
        assertEquals(2, result.rotation());
        assertEquals(new Position(0, 0), result.origin());
        assertTrue(board.canPlace(TetrominoType.I, result.rotation(), result.origin()));
    }

    @Test
    void failsAndKeepsOriginalStateWhenNoKickFits() {
        // 폭이 1칸뿐인 보드 - 세로 I를 가로로 돌릴 공간 자체가 존재하지 않음
        Board board = new Board(1, 4);
        Position origin = new Position(-1, 0); // I(rotation 1) 상대 x=1 -> 절대 x=0, 유일한 칸

        RotationResult result = rotationSystem.tryRotate(board, TetrominoType.I, 1, origin, true);

        assertFalse(result.rotated());
        assertEquals(1, result.rotation());
        assertEquals(origin, result.origin());
    }

    @Test
    void rejectsRotationThatWouldPushCellsAboveTheBoard() {
        Board board = new Board(6, 6);
        // I(rotation 0, 가로)의 상대좌표는 y=1 고정이라 origin(1,-1)이면 0행에 걸쳐 있다.
        // rotation 1(세로)은 y=0~3을 쓰므로 제자리 회전 시 -1행으로 칸이 나가고,
        // 벽차기 후보는 모두 같은 높이이거나 더 위라서 전부 거절되어야 한다.
        Position origin = new Position(1, -1);

        RotationResult result = rotationSystem.tryRotate(board, TetrominoType.I, 0, origin, true);

        assertFalse(result.rotated());
        assertEquals(0, result.rotation());
        assertEquals(origin, result.origin());
    }

    @Test
    void doesNotRotateThroughAnAlreadyOccupiedCell() {
        Board board = new Board(6, 6);
        Position tOrigin = new Position(2, 2);
        // 회전 목표 자리(rotation 1)를 다른 블록으로 미리 막아둔다.
        board.place(TetrominoType.O, 0, new Position(1, 2));

        RotationResult result = rotationSystem.tryRotate(board, TetrominoType.T, 0, tOrigin, true);

        // 막힌 칸을 피해 어떤 형태로든(제자리 실패 시 벽차기로) 유효한 자리를 찾아야 하며,
        // 최종적으로 돌아간 자리는 반드시 보드에 놓을 수 있는 자리여야 한다.
        if (result.rotated()) {
            assertTrue(board.canPlace(TetrominoType.T, result.rotation(), result.origin()));
        } else {
            assertEquals(0, result.rotation());
            assertEquals(tOrigin, result.origin());
        }
    }
}
