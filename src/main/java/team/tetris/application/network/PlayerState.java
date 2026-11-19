package team.tetris.application.network;

import java.util.List;
import java.util.Objects;

/** 화면용 데이터만 보낸다. board는 10×20 행 우선, active는 보드 좌표, next는 미리보기 상대 좌표. */
public record PlayerState(List<Tile> board, List<PlacedTile> active, List<PlacedTile> next,
                          long score, int level, int clearedLines, int doubleScoreClears,
                          boolean defeated, List<Integer> pendingRows) {
    public static final int WIDTH = 10;
    public static final int HEIGHT = 20;
    public static final int MAX_PIECE_CELLS = 6; // 무게추: 위 2칸 + 아래 4칸
    public enum ShapeCode { EMPTY, I, O, T, S, Z, J, L, WEIGHT, SOLO, ATTACK, TEMPORARY }
    public enum ItemCode { NONE, LINE_CLEAR, BOMB, CHAIN_CLEAR, DOUBLE_SCORE, WEIGHT }

    public record Tile(ShapeCode shape, ItemCode item) {
        public Tile {
            Objects.requireNonNull(shape);
            Objects.requireNonNull(item);
            if (shape == ShapeCode.EMPTY && item != ItemCode.NONE)
                throw new IllegalArgumentException("Empty tile cannot contain an item");
        }
    }
    public record PlacedTile(int x, int y, Tile tile) {
        public PlacedTile {
            Objects.requireNonNull(tile);
            if (x < 0 || x >= WIDTH || y < -4 || y >= HEIGHT || tile.shape() == ShapeCode.EMPTY)
                throw new IllegalArgumentException("Invalid piece tile");
        }
    }

    public PlayerState {
        board = List.copyOf(board);
        active = List.copyOf(active);
        next = List.copyOf(next);
        pendingRows = attackRows(pendingRows);
        if (board.size() != WIDTH * HEIGHT || active.size() > MAX_PIECE_CELLS
                || next.isEmpty() || next.size() > MAX_PIECE_CELLS)
            throw new IllegalArgumentException("Invalid board/piece size");
        if (score < 0 || level < 1 || clearedLines < 0 || doubleScoreClears < 0)
            throw new IllegalArgumentException("Invalid player counters");
    }

    static List<Integer> attackRows(List<Integer> source) {
        List<Integer> rows = List.copyOf(source);
        if (rows.size() > 10) throw new IllegalArgumentException("Too many attack rows");
        for (int row : rows) {
            if (row < 0 || row >= (1 << WIDTH) - 1)
                throw new IllegalArgumentException("Attack row must fit the board and contain a gap");
        }
        return rows;
    }
}
