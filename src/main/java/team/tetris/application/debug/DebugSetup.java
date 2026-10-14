package team.tetris.application.debug;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Objects;
import java.util.Random;
import team.tetris.application.model.Difficulty;
import team.tetris.core.Cell;
import team.tetris.core.TetrominoType;

/** 적용한 초기 상태. 위 두 줄은 현재 블록의 생성 공간으로 비운다. */
public record DebugSetup(int filledRows, int holesPerRow, HolePattern pattern,
                         int clearedLines, Difficulty difficulty, long seed) {
    public static final int MAX_LINES = 1_000_000;

    public enum HolePattern {
        ALIGNED("같은 열로 정렬"), RANDOM("줄마다 랜덤");
        private final String label;
        HolePattern(String label) { this.label = label; }
        @Override public String toString() { return label; }
    }

    public DebugSetup {
        if (filledRows < 0 || filledRows > 18) throw new IllegalArgumentException("채울 줄 수는 0~18입니다.");
        if (holesPerRow < 0 || holesPerRow > 10) throw new IllegalArgumentException("빈칸 수는 0~10입니다.");
        validateLines(clearedLines);
        Objects.requireNonNull(pattern, "pattern");
        Objects.requireNonNull(difficulty, "difficulty");
    }

    public static void validateLines(int lines) {
        if (lines < 0 || lines > MAX_LINES) throw new IllegalArgumentException("삭제 줄 수는 0~1,000,000입니다.");
    }

    public static DebugSetup empty(Difficulty difficulty) {
        return new DebugSetup(0, 1, HolePattern.ALIGNED, 0, difficulty, 13);
    }

    public Cell[][] board() {
        Cell[][] board = new Cell[20][10];
        for (Cell[] row : board) Arrays.fill(row, Cell.EMPTY);
        Random random = new Random(seed);
        for (int y = 20 - filledRows; y < 20; y++) {
            Arrays.fill(board[y], Cell.occupiedBy(TetrominoType.J));
            var columns = new ArrayList<Integer>();
            for (int x = 0; x < 10; x++) columns.add(x);
            if (pattern == HolePattern.RANDOM) Collections.shuffle(columns, random);
            for (int i = 0; i < holesPerRow; i++) {
                int x = pattern == HolePattern.ALIGNED ? (10 - holesPerRow) / 2 + i : columns.get(i);
                board[y][x] = Cell.EMPTY;
            }
        }
        return board;
    }
}
