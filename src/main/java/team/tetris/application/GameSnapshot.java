package team.tetris.application;

import java.util.List;
import java.util.Objects;
import team.tetris.core.Cell;
import team.tetris.core.EngineSnapshot;
import team.tetris.core.result.ClearedRow;

/**
 * 보드 배열은 생성 시와 조회 시 복사. Cell과 나머지 코어 값은 불변 값.
 *
 * @param doubleScoreClears 점수 2배가 남은 줄 삭제 횟수 (아이템 모드의 {@code $}, 없으면 0)
 * @param clearingRows      줄 삭제 애니메이션 중이면 방금 지운 줄들(지워지기 직전 내용), 아니면 빈 목록.
 *                          엔진 보드는 이미 줄이 지워진 상태다.
 * @param clearingProgress  줄 삭제 애니메이션의 진행 정도. 0(시작)부터 1 미만까지, 애니메이션이 없으면 0
 */
public record GameSnapshot(
        EngineSnapshot engine, long score, int level, int clearedLines,
        long gravityIntervalNanos, GameStatus status, int doubleScoreClears, List<ClearedRow> clearingRows,
        double clearingProgress) {
    public GameSnapshot {
        engine = copyEngine(Objects.requireNonNull(engine, "engine"));
        Objects.requireNonNull(status, "status");
        if (doubleScoreClears < 0) {
            throw new IllegalArgumentException("doubleScoreClears must be non-negative");
        }
        clearingRows = List.copyOf(clearingRows);
        if (!(clearingProgress >= 0 && clearingProgress < 1)) {
            throw new IllegalArgumentException("clearingProgress must be in [0, 1)");
        }
    }

    /** 줄 삭제 애니메이션이 막 시작한 상태 (진행 정도 0). */
    public GameSnapshot(EngineSnapshot engine, long score, int level, int clearedLines,
                        long gravityIntervalNanos, GameStatus status, int doubleScoreClears,
                        List<ClearedRow> clearingRows) {
        this(engine, score, level, clearedLines, gravityIntervalNanos, status, doubleScoreClears, clearingRows, 0);
    }

    /** 줄 삭제 애니메이션이 없는 상태. */
    public GameSnapshot(EngineSnapshot engine, long score, int level, int clearedLines,
                        long gravityIntervalNanos, GameStatus status, int doubleScoreClears) {
        this(engine, score, level, clearedLines, gravityIntervalNanos, status, doubleScoreClears, List.of());
    }

    /** 점수 2배 효과가 없는 상태. */
    public GameSnapshot(EngineSnapshot engine, long score, int level, int clearedLines,
                        long gravityIntervalNanos, GameStatus status) {
        this(engine, score, level, clearedLines, gravityIntervalNanos, status, 0);
    }

    /** 줄 삭제 애니메이션 중인가. 이 동안 세션은 시간을 애니메이션에만 쓰고 블록을 내리지 않는다. */
    public boolean isClearing() {
        return !clearingRows.isEmpty();
    }

    @Override
    public EngineSnapshot engine() {
        return copyEngine(engine);
    }

    private static EngineSnapshot copyEngine(EngineSnapshot source) {
        Cell[][] board = source.board().clone();
        for (int row = 0; row < board.length; row++) {
            board[row] = board[row].clone();
        }
        return new EngineSnapshot(board, source.activePiece(), source.nextPiece(), source.phase());
    }
}
