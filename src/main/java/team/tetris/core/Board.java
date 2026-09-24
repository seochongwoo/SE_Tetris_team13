package team.tetris.core;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import team.tetris.core.result.ClearResult;
import team.tetris.core.result.ClearedRow;

/**
 * 고정 크기 격자와 그 위의 충돌 판정 / 배치 / 라인 클리어를 담당한다.
 *
 * 낙하 중인 블록의 위치 관리나 중력 처리(자동 하강, 소프트/하드 드롭)는 여기서 하지 않는다 
 * 그건 엔진(PlayerEngine)의 책임이고, Board는 이미 고정된 칸들의 상태만 갖는다.
 *
 * y좌표가 0보다 작은 영역(보드 위쪽, 화면에 보이지 않는 스폰 여유 공간)은 항상 비어있는
 * 것으로 취급한다 - 블록이 보드 상단 경계 밖에 걸친 채로 스폰/회전하는 것을 허용하기 위함.
 * x가 [0, width) 범위를 벗어나거나 y가 height 이상이면 그 칸은 보드 밖이라 배치할 수 없다.
 */
public final class Board {

    private final int width;
    private final int height;
    private Cell[][] grid; // grid[row][col], row 0 = 맨 위

    public Board(int width, int height) {
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("width and height must be positive: " + width + "x" + height);
        }
        this.width = width;
        this.height = height;
        this.grid = emptyGrid();
    }

    public int width() {
        return width;
    }

    public int height() {
        return height;
    }

    /**
     * type을 rotation 상태로 origin에 두었을 때, 보드 좌우/아래 경계를 벗어나거나 이미 채워진
     * 칸과 겹치면 false. 보드 위쪽(y &lt; 0)으로 걸치는 것은 허용한다.
     */
    public boolean canPlace(TetrominoType type, int rotation, Position origin) {
        for (Position offset : type.cellsAt(rotation)) {
            Position cell = origin.translate(offset.x(), offset.y());
            if (cell.x() < 0 || cell.x() >= width || cell.y() >= height) {
                return false;
            }
            if (cell.y() >= 0 && !grid[cell.y()][cell.x()].isEmpty()) {
                return false;
            }
        }
        return true;
    }

    /**
     * type을 rotation 상태로 origin에 확정 배치한다. 호출 전에 canPlace로 검증되어
     * 있어야 하며, 유효하지 않은 배치를 시도하면 예외를 던진다. origin보다 위에 걸친
     * 칸은 보드 밖이라 기록하지 않는다.
     */
    public void place(TetrominoType type, int rotation, Position origin) {
        if (!canPlace(type, rotation, origin)) {
            throw new IllegalStateException(
                    "cannot place " + type + " rotation " + rotation + " at " + origin);
        }
        for (Position offset : type.cellsAt(rotation)) {
            Position cell = origin.translate(offset.x(), offset.y());
            if (cell.y() >= 0) {
                grid[cell.y()][cell.x()] = Cell.occupiedBy(type);
            }
        }
    }

    public boolean isRowFull(int row) {
        for (int col = 0; col < width; col++) {
            if (grid[row][col].isEmpty()) {
                return false;
            }
        }
        return true;
    }

    /**
     * 꽉 찬 행을 모두 지우고, 그 위에 남아있던 행들을 그 개수만큼 아래로 밀어낸 뒤 맨 위를
     * 빈 행으로 채운다. 지워진 행이 없으면 NONE 을 반환한다.
     */
    public ClearResult clearFullLines() {
        List<ClearedRow> cleared = new ArrayList<>();
        List<Cell[]> remaining = new ArrayList<>();
        for (int row = 0; row < height; row++) {
            if (isRowFull(row)) {
                cleared.add(new ClearedRow(row));
            } else {
                remaining.add(grid[row]);
            }
        }
        if (cleared.isEmpty()) {
            return ClearResult.NONE;
        }

        Cell[][] newGrid = new Cell[height][];
        int emptyRowsNeeded = height - remaining.size();
        for (int row = 0; row < emptyRowsNeeded; row++) {
            newGrid[row] = emptyRow();
        }
        for (int row = 0; row < remaining.size(); row++) {
            newGrid[emptyRowsNeeded + row] = remaining.get(row);
        }
        grid = newGrid;

        return new ClearResult(cleared);
    }

    /**
     * 현재 보드 상태의 읽기 전용 스냅샷(깊은 복사)을 반환한다. 반환된 배열을 수정해도 Board
     * 내부 상태에는 영향이 없다.
     */
    public Cell[][] snapshot() {
        Cell[][] copy = new Cell[height][width];
        for (int row = 0; row < height; row++) {
            System.arraycopy(grid[row], 0, copy[row], 0, width);
        }
        return copy;
    }

    private Cell[][] emptyGrid() {
        Cell[][] newGrid = new Cell[height][];
        for (int row = 0; row < height; row++) {
            newGrid[row] = emptyRow();
        }
        return newGrid;
    }

    private Cell[] emptyRow() {
        Cell[] row = new Cell[width];
        Arrays.fill(row, Cell.EMPTY);
        return row;
    }
}
