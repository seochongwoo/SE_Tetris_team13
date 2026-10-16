package team.tetris.application;

import java.util.List;
import team.tetris.core.Cell;
import team.tetris.core.result.ClearedRow;

/** 줄 삭제 연출의 불변 상태. 실제 보드는 이미 압축됐으며 표시할 때만 삭제 전 행을 복원한다. */
public record LineClearAnimation(List<ClearedRow> rows, long elapsedNanos) {
    public static final long FLASH_NANOS = 150_000_000L;
    public static final long DURATION_NANOS = 300_000_000L;

    public LineClearAnimation {
        rows = List.copyOf(rows);
        if (rows.isEmpty()) throw new IllegalArgumentException("Cleared rows are required");
        if (elapsedNanos < 0 || elapsedNanos >= DURATION_NANOS) {
            throw new IllegalArgumentException("Animation time must be within its duration");
        }
    }

    public boolean flashing() {
        return elapsedNanos < FLASH_NANOS;
    }

    /** 아이템 효과 적용 후, 줄 압축 직전의 보드. 입력과 반환 배열을 서로 공유하지 않는다. */
    public Cell[][] boardBeforeClear(Cell[][] compacted) {
        Cell[][] restored = new Cell[compacted.length][];
        for (ClearedRow row : rows) {
            restored[row.rowIndex()] = row.cells().toArray(Cell[]::new);
        }
        int sourceRow = rows.size();
        for (int row = 0; row < restored.length; row++) {
            if (restored[row] == null) restored[row] = compacted[sourceRow++].clone();
        }
        return restored;
    }
}
