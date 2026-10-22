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

    /** 보드·공급자·아이템 효과를 바꾸지 않고, 고정 직전 위치만 계산한다. */
    public static ActivePiece landingPiece(Cell[][] cells, ActivePiece piece) {
        if (piece == null) return null;
        Board board = new Board(cells);
        ActivePiece landing = piece;
        while (canDescend(board, landing)) {
            landing = landing.movedTo(landing.origin().translate(0, 1));
        }
        return landing;
    }
}
