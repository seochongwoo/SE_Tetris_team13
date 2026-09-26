package team.tetris.application;

import java.util.Objects;
import java.util.UUID;

/** 자연 종료 또는 중도 종료 시 확정되는 값. 기록 저장 여부는 종료 조율자가 판단한다. */
public record GameResult(UUID gameId, long score, int level, int clearedLines, GameStatus reason) {
    public GameResult {
        Objects.requireNonNull(gameId, "gameId");
        Objects.requireNonNull(reason, "reason");
        if (reason != GameStatus.GAME_OVER && reason != GameStatus.ABORTED) {
            throw new IllegalArgumentException("Result requires a terminal status");
        }
        if (score < 0 || level < 0 || clearedLines < 0) {
            throw new IllegalArgumentException("Result values must be non-negative");
        }
    }
}
