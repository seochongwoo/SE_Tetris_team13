package team.tetris.core.rule;

import team.tetris.core.ActivePiece;
import team.tetris.core.Board;
import team.tetris.core.Cell;
import team.tetris.core.Position;

/** 실제 낙하와 착지 미리보기가 공유하는 충돌 규칙. */
public final class DropRule {
    private DropRule() { }

    public static boolean canDescend(Board board, ActivePiece piece) {
        Position lowered = piece.origin().translate(0, 1);
        return board.canPlace(piece.shape(), piece.rotation(), lowered);
    }

    /** 블록의 이동 규칙으로 한 칸 낙하. 실제 엔진과 미리보기에 같은 규칙을 적용한다. */
    public static ActivePiece descend(Board board, ActivePiece piece) {
        return piece.shape().movementRule().descend(board, piece);
    }

    /** 보드·공급자·아이템 효과를 바꾸지 않고, 고정 직전 위치만 계산한다. */
    public static ActivePiece landingPiece(Cell[][] cells, ActivePiece piece) {
        if (piece == null) return null;
        Board board = new Board(cells);
        ActivePiece landing = piece;
        ActivePiece next;
        while ((next = descend(board, landing)) != null) landing = next;
        return landing;
    }
}
