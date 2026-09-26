package team.tetris.application;

import java.util.Objects;
import team.tetris.core.Cell;
import team.tetris.core.EngineSnapshot;

/** 보드 배열은 생성 시와 조회 시 복사. Cell과 나머지 코어 값은 불변 값. */
public record GameSnapshot(
        EngineSnapshot engine, long score, int level, int clearedLines,
        long gravityIntervalNanos, GameStatus status) {
    public GameSnapshot {
        engine = copyEngine(Objects.requireNonNull(engine, "engine"));
        Objects.requireNonNull(status, "status");
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
        return new EngineSnapshot(board, source.activePiece(), source.nextType(), source.phase());
    }
}
