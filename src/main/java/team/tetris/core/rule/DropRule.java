package team.tetris.core.rule;

import team.tetris.core.ActivePiece;
import team.tetris.core.Board;
import team.tetris.core.Cell;
import team.tetris.core.Position;
import team.tetris.core.item.WeightBlock;

/** 실제 낙하와 착지 미리보기가 공유하는 충돌 규칙. */
public final class DropRule {
    private DropRule() { }

    public static boolean canDescend(Board board, ActivePiece piece) {
        Position lowered = piece.origin().translate(0, 1);
        return board.canPlace(piece.shape(), piece.rotation(), lowered);
    }

    /** 한 칸 낙하. 무게추는 경로의 칸을 지우며, 바닥에 닿으면 보드를 바꾸지 않고 null 반환. */
    public static ActivePiece descend(Board board, ActivePiece piece) {
        ActivePiece next = piece.movedTo(piece.origin().translate(0, 1));
        if (piece.shape() == WeightBlock.SHAPE) {
            for (Position cell : next.cells()) {
                if (cell.y() >= board.height()) return null;
            }
            for (Position cell : next.cells()) board.removeCell(cell);
        } else if (!canDescend(board, piece)) {
            return null;
        }
        return next;
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
