package team.tetris.application;

/** 삭제 10줄마다 레벨 상승, 초기 1초에서 최소 100ms까지 가속한다. */
public final class SpeedPolicy {
    public int levelFor(int totalClearedLines) {
        if (totalClearedLines < 0) {
            throw new IllegalArgumentException("Cleared lines must be non-negative");
        }
        return totalClearedLines / 10;
    }

    public long gravityIntervalNanos(int level) {
        if (level < 0) {
            throw new IllegalArgumentException("Level must be non-negative");
        }
        return Math.max(100L, 1000L - level * 100L) * 1_000_000L;
    }
}
