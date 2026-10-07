package team.tetris.application;

import java.util.Objects;
import team.tetris.application.model.Difficulty;

/** 삭제 10줄마다 레벨 상승, 초기 1초에서 최소 100ms까지 가속. */
public final class SpeedPolicy implements SpeedRule {
    private final long decreaseMillis;

    public SpeedPolicy() { this(Difficulty.NORMAL); }

    /** 기존 100ms 감소량을 기준으로 easy -20%, hard +20%. */
    public SpeedPolicy(Difficulty difficulty) {
        decreaseMillis = switch (Objects.requireNonNull(difficulty, "difficulty")) {
            case EASY -> 80L;
            case NORMAL -> 100L;
            case HARD -> 120L;
        };
    }

    @Override
    public int levelFor(int totalClearedLines) {
        if (totalClearedLines < 0) {
            throw new IllegalArgumentException("Cleared lines must be non-negative");
        }
        return totalClearedLines / 10;
    }

    @Override
    public long gravityIntervalNanos(int level) {
        if (level < 0) {
            throw new IllegalArgumentException("Level must be non-negative");
        }
        return Math.max(100L, 1000L - level * decreaseMillis) * 1_000_000L;
    }
}
