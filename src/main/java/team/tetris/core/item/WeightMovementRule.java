package team.tetris.core.item;

import team.tetris.core.ActivePiece;
import team.tetris.core.Board;
import team.tetris.core.Position;
import team.tetris.core.rule.PieceMovementRule;

/** 실제 하강 시 아래에 막힌 뒤 좌우 이동을 잠그고, 회전 없이 칸을 지우며 내려가는 무게추 규칙. */
enum WeightMovementRule implements PieceMovementRule {
    INSTANCE;

    @Override
    public boolean allowsHorizontalMovement(boolean touchedDown) {
        return !touchedDown;
    }

    @Override
    public boolean allowsRotation(boolean touchedDown) {
        return false;
    }

    @Override
    public ActivePiece descend(Board board, ActivePiece piece) {
        ActivePiece next = piece.movedTo(piece.origin().translate(0, 1));
        for (Position cell : next.cells()) {
            if (cell.y() >= board.height()) return null;
        }
        for (Position cell : next.cells()) board.removeCell(cell);
        return next;
    }
}
