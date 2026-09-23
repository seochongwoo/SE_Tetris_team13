package team.tetris.core.result;

import java.util.List;

/**
 * 한 번의 {@code Board.clearFullLines()} 호출로 지워진 행들의 결과.
 * 지워진 행이 없으면 {@link #NONE}을 사용한다.
 */
public record ClearResult(List<ClearedRow> clearedRows) {

    public static final ClearResult NONE = new ClearResult(List.of());

    public ClearResult {
        clearedRows = List.copyOf(clearedRows);
    }

    public int lineCount() {
        return clearedRows.size();
    }

    public boolean isEmpty() {
        return clearedRows.isEmpty();
    }
}
