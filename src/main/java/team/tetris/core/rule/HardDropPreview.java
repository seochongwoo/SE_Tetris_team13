package team.tetris.core.rule;

import java.util.HashSet;
import java.util.Set;
import java.util.TreeSet;
import team.tetris.core.ActivePiece;
import team.tetris.core.Board;
import team.tetris.core.Cell;
import team.tetris.core.Position;
import team.tetris.core.item.Item;
import team.tetris.core.item.ItemContext;

/** 현재 위치에서 떨어뜨리면 사라질 기존 보드 칸. 좌표는 줄 압축 이전 기준이다. */
public record HardDropPreview(ActivePiece landing, Set<Position> removedCells) {
    public HardDropPreview {
        removedCells = Set.copyOf(removedCells);
    }

    /** 공급자·점수·실제 발동 콜백을 건드리지 않고 복사한 보드에서만 계산한다. */
    public static HardDropPreview project(Cell[][] cells, ActivePiece active) {
        if (active == null) return new HardDropPreview(null, Set.of());
        Board board = new Board(cells);
        ActivePiece landing = active;
        ActivePiece next;
        while ((next = DropRule.descend(board, landing)) != null) landing = next;
        Set<Position> removed = new HashSet<>();
        // 무게추가 지나가며 지운 칸은 최종 착지 위치의 칸을 다시 놓기 전에 수집한다.
        for (int y = 0; y < board.height(); y++) {
            for (int x = 0; x < board.width(); x++) {
                Position position = new Position(x, y);
                if (!cells[y][x].isEmpty() && board.cellAt(position).isEmpty()) removed.add(position);
            }
        }
        for (Position position : landing.cells()) {
            if (position.y() < 0) return new HardDropPreview(landing, removed);
        }
        board.place(landing);
        PreviewContext context = new PreviewContext(board, removed);
        Position[] positions = landing.cells();
        for (int i = 0; i < positions.length; i++) {
            Item item = landing.itemAt(i);
            if (item == null) continue;
            board.removeItem(positions[i]);
            item.previewOnLock(context, positions[i]);
        }
        var clear = board.clearRows(context.rows);
        for (var row : clear.clearedRows()) {
            for (int x = 0; x < row.cells().size(); x++) {
                if (!row.cells().get(x).isEmpty()) removed.add(new Position(x, row.rowIndex()));
            }
        }
        // 지금 떨어지는 블록과 빈 공간은 대상이 아니다. 위에서 내려올 칸도 삭제로 오인하지 않는다.
        removed.removeIf(position -> cells[position.y()][position.x()].isEmpty());
        return new HardDropPreview(landing, removed);
    }

    private static final class PreviewContext implements ItemContext {
        private final Board board;
        private final Set<Position> removed;
        private final Set<Integer> rows = new TreeSet<>();

        private PreviewContext(Board board, Set<Position> removed) {
            this.board = board;
            this.removed = removed;
        }

        public int width() { return board.width(); }
        public int height() { return board.height(); }
        public Cell cellAt(Position position) { return board.cellAt(position); }
        public void clearRow(int row) { rows.add(row); }
        public void removeCell(Position position) {
            if (position.y() >= 0 && !board.cellAt(position).isEmpty()) removed.add(position);
            board.removeCell(position);
        }
    }
}
