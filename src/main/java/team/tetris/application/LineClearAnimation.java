package team.tetris.application;

import java.util.List;
import team.tetris.core.ActivePiece;
import team.tetris.core.Cell;
import team.tetris.core.result.ClearedRow;
import team.tetris.core.result.PushResult;
import team.tetris.core.result.RemovedCell;

/**
 * 줄·아이템 삭제 연출의 불변 상태. 낙하 중 삭제라면 연출 직전 활성 블록도 표시한다.
 *
 * <p>pushResult는 줄 삭제 뒤에 보드 아래로 들어온 공격 줄과 그 때문에 위로 밀려 나간 줄(대전)이다.
 * 연출 동안에는 삭제 직전 보드를 보여 주고, 공격 줄은 연출이 끝난 뒤 나타난다.
 */
public record LineClearAnimation(List<ClearedRow> rows, List<RemovedCell> removedCells,
                                 ActivePiece activePiece, long elapsedNanos, PushResult pushResult) {
    /** 전체 연출의 앞 20%는 흰색 점멸, 나머지는 가운데부터 양쪽으로 삭제한다. */
    public static final long FLASH_NANOS = 60_000_000L;
    public static final long DURATION_NANOS = 300_000_000L;

    public LineClearAnimation {
        pushResult = pushResult == null ? PushResult.NONE : pushResult;
        rows = List.copyOf(rows);
        removedCells = List.copyOf(removedCells);
        if (rows.isEmpty() && removedCells.isEmpty()) throw new IllegalArgumentException("Deleted cells are required");
        if (elapsedNanos < 0 || elapsedNanos >= DURATION_NANOS) {
            throw new IllegalArgumentException("Animation time must be within its duration");
        }
    }

    /** 공격 줄이 들어오지 않은 연출. */
    public LineClearAnimation(List<ClearedRow> rows, List<RemovedCell> removedCells,
                              ActivePiece activePiece, long elapsedNanos) {
        this(rows, removedCells, activePiece, elapsedNanos, PushResult.NONE);
    }

    /** 시간만 흐른 같은 연출. */
    public LineClearAnimation advancedBy(long nanos) {
        return new LineClearAnimation(rows, removedCells, activePiece, elapsedNanos + nanos, pushResult);
    }

    /** 줄 전체만 지우는 연출. */
    public LineClearAnimation(List<ClearedRow> rows, long elapsedNanos) {
        this(rows, List.of(), null, elapsedNanos);
    }

    /** 줄 삭제 뒤 보드 아래로 들어온 공격 줄 수. */
    public int addedLines() {
        return pushResult.addedLines();
    }

    public boolean flashing() {
        return elapsedNanos < FLASH_NANOS;
    }

    /**
     * 공격 줄 추가와 줄 압축을 되돌리고 아이템으로 지운 칸도 복원한다. 입력과 반환 배열은 공유하지 않는다.
     */
    public Cell[][] boardBeforeClear(Cell[][] current) {
        Cell[][] compacted = withoutAddedLines(current);
        Cell[][] restored = new Cell[compacted.length][];
        for (ClearedRow row : rows) {
            restored[row.rowIndex()] = row.cells().toArray(Cell[]::new);
        }
        int sourceRow = rows.size();
        for (int row = 0; row < restored.length; row++) {
            if (restored[row] == null) restored[row] = compacted[sourceRow++].clone();
        }
        for (RemovedCell removed : removedCells) {
            restored[removed.position().y()][removed.position().x()] = removed.cell();
        }
        return restored;
    }

    /**
     * 아래에 붙은 공격 줄을 빼고 나머지를 원래 높이로 내린다. 맨 위에는 공격 줄에 밀려 보드 밖으로
     * 나갔던 줄을 되돌려 놓는다. 넘쳐서 게임이 끝난 경우에도 나갔던 블록 칸이 연출 동안 보인다.
     */
    private Cell[][] withoutAddedLines(Cell[][] current) {
        int count = addedLines();
        if (count == 0) return current;
        int height = current.length;
        List<List<Cell>> pushedOut = pushResult.pushedOutRows();
        Cell[][] lowered = new Cell[height][];
        for (int row = 0; row < height; row++) {
            if (row < count) {
                lowered[row] = pushedOut.get(row).toArray(Cell[]::new);
            } else {
                lowered[row] = current[row - count];
            }
        }
        return lowered;
    }
}
