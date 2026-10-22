package team.tetris.application;

import java.util.Objects;
import team.tetris.application.model.Difficulty;
import team.tetris.application.model.GameMode;
import team.tetris.core.Cell;
import team.tetris.core.EngineSnapshot;

/**
 * 보드 배열은 생성 시와 조회 시 복사. Cell과 나머지 코어 값은 불변 값.
 *
 * @param doubleScoreClears 점수 2배가 남은 줄 삭제 횟수 (아이템 모드의 {@code $}, 없으면 0)
 * @param lineClearAnimation 줄 삭제 연출 상태, 연출 중이 아니면 null
 */
public record GameSnapshot(
        EngineSnapshot engine, long score, int level, int clearedLines,
        long gravityIntervalNanos, GameStatus status, int doubleScoreClears,
        LineClearAnimation lineClearAnimation, GameMode mode, Difficulty difficulty) {
    public GameSnapshot {
        engine = copyEngine(Objects.requireNonNull(engine, "engine"));
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(mode, "mode");
        Objects.requireNonNull(difficulty, "difficulty");
        if (doubleScoreClears < 0) {
            throw new IllegalArgumentException("doubleScoreClears must be non-negative");
        }
    }

    /** 모드·난이도를 생략하면 일반·NORMAL로 표시한다. */
    public GameSnapshot(EngineSnapshot engine, long score, int level, int clearedLines,
                        long gravityIntervalNanos, GameStatus status, int doubleScoreClears,
                        LineClearAnimation lineClearAnimation) {
        this(engine, score, level, clearedLines, gravityIntervalNanos, status, doubleScoreClears,
                lineClearAnimation, GameMode.NORMAL, Difficulty.NORMAL);
    }

    /** 줄 삭제 연출이 없는 상태. */
    public GameSnapshot(EngineSnapshot engine, long score, int level, int clearedLines,
                        long gravityIntervalNanos, GameStatus status, int doubleScoreClears) {
        this(engine, score, level, clearedLines, gravityIntervalNanos, status, doubleScoreClears, null);
    }

    /** 점수 2배 효과가 없는 상태. */
    public GameSnapshot(EngineSnapshot engine, long score, int level, int clearedLines,
                        long gravityIntervalNanos, GameStatus status) {
        this(engine, score, level, clearedLines, gravityIntervalNanos, status, 0);
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
