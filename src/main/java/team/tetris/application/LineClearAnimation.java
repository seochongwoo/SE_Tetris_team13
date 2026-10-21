package team.tetris.application;

import java.util.List;
import team.tetris.core.ActivePiece;
import team.tetris.core.Cell;
import team.tetris.core.result.ClearedRow;
import team.tetris.core.result.RemovedCell;

/** 줄·아이템 삭제 연출의 불변 상태. 낙하 중 삭제라면 연출 직전 활성 블록도 표시한다. */
public record LineClearAnimation(List<ClearedRow> rows, List<RemovedCell> removedCells,
                                 ActivePiece activePiece, long elapsedNanos) {
    /** 전체 연출의 앞 20%는 흰색 점멸, 나머지는 가운데부터 양쪽으로 삭제한다. */
    public static final long FLASH_NANOS = 60_000_000L;
    public static final long DURATION_NANOS = 300_000_000L;

    public LineClearAnimation {
        rows = List.copyOf(rows);
        removedCells = List.copyOf(removedCells);
        if (rows.isEmpty() && removedCells.isEmpty()) throw new IllegalArgumentException("Deleted cells are required");
        if (elapsedNanos < 0 || elapsedNanos >= DURATION_NANOS) {
            throw new IllegalArgumentException("Animation time must be within its duration");
        }
    }

    /** 줄 전체만 지우는 연출. */
    public LineClearAnimation(List<ClearedRow> rows, long elapsedNanos) {
        this(rows, List.of(), null, elapsedNanos);
    }

    public boolean flashing() {
        return elapsedNanos < FLASH_NANOS;
    }

    /** 줄 압축을 되돌리고 아이템으로 지운 칸도 복원한다. 입력과 반환 배열은 공유하지 않는다. */
    public Cell[][] boardBeforeClear(Cell[][] compacted) {
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
}
