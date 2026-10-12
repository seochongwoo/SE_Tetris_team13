package team.tetris.core.item;

import team.tetris.core.ActivePiece;
import team.tetris.core.Board;
import team.tetris.core.Cell;
import team.tetris.core.Position;
import team.tetris.core.Shape;

/** 아이템 효과를 실제 {@link Board} 위에서 확인하기 위한 테스트용 ItemContext. */
final class BoardContext implements ItemContext {

    final Board board;
    int clearRowCalls;

    BoardContext(int width, int height) {
        board = new Board(width, height);
    }

    /** shape(회전 0)을 origin에 놓고 차지한 칸들을 반환한다. */
    Position[] place(Shape shape, Position origin) {
        ActivePiece piece = new ActivePiece(shape, 0, origin);
        board.place(piece);
        return piece.cells();
    }

    int filledCells() {
        int count = 0;
        for (Cell[] row : board.snapshot()) {
            for (Cell cell : row) {
                if (!cell.isEmpty()) {
                    count++;
                }
            }
        }
        return count;
    }

    @Override
    public int width() {
        return board.width();
    }

    @Override
    public int height() {
        return board.height();
    }

    @Override
    public Cell cellAt(Position position) {
        return board.cellAt(position);
    }

    @Override
    public void clearRow(int row) {
        clearRowCalls++;
    }

    @Override
    public void removeCell(Position position) {
        board.removeCell(position);
    }
}
