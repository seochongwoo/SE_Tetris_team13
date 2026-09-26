package team.tetris.application;

/** 동작 종류와 무관하게 실제 하강 거리와 동작 직전 레벨로 점수를 계산한다. */
public final class ScorePolicy {
    public long scoreFor(int droppedCells, int clearedLines, int levelBeforeStep) {
        if (droppedCells < 0 || clearedLines < 0 || levelBeforeStep < 0) {
            throw new IllegalArgumentException("Score inputs must be non-negative");
        }
        long dropScore = (long) droppedCells * (levelBeforeStep + 1L);
        long lineBonus = Math.multiplyExact(100L, (long) clearedLines * clearedLines);
        return Math.addExact(dropScore, lineBonus);
    }
}
