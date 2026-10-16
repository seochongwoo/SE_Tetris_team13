package team.tetris.core.rule;

import team.tetris.core.ActivePiece;
import team.tetris.core.Board;

/**
 * 블록별 이동 규칙. {@link team.tetris.core.Shape#movementRule()}에서 선택한다.
 * 구현체는 상태를 저장하지 않는다. 최초 아래쪽 접촉 여부는 엔진이 블록마다 관리한다.
 */
public interface PieceMovementRule {
    PieceMovementRule NORMAL = new PieceMovementRule() { };

    /** 최초 접촉 이후에는 아래가 다시 비어도 touchedDown이 true로 유지된다. */
    default boolean allowsHorizontalMovement(boolean touchedDown) {
        return true;
    }

    /** 허용하면 기존 회전·벽차기·충돌 판정을 적용한다. */
    default boolean allowsRotation(boolean touchedDown) {
        return true;
    }

    /**
     * 한 칸 아래로 이동한 블록 또는 이동 불가 시 null. 성공 시 전달받은 보드의 칸을 지울 수
     * 있지만 실패 시 보드는 그대로 둔다. 미리보기도 복사한 보드로 이 메서드를 호출하므로
     * 외부 상태를 변경해서는 안 된다. 반환 위치는 반드시 현재보다 한 칸 아래여야 한다.
     */
    default ActivePiece descend(Board board, ActivePiece piece) {
        ActivePiece next = piece.movedTo(piece.origin().translate(0, 1));
        return board.canPlace(next.shape(), next.rotation(), next.origin()) ? next : null;
    }
}
