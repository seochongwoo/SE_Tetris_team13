package team.tetris.core.result;

import java.util.Objects;
import team.tetris.core.Cell;
import team.tetris.core.Position;

/** 아이템으로 지운 칸. 좌표와 내용은 줄 압축 전 기준이다. 빈 칸은 기록하지 않는다. */
public record RemovedCell(Position position, Cell cell) {
    public RemovedCell {
        Objects.requireNonNull(position, "position");
        Objects.requireNonNull(cell, "cell");
        if (cell.isEmpty()) throw new IllegalArgumentException("Removed cell must be occupied");
    }
}
