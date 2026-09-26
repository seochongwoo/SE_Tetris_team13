package team.tetris.application;

/** 실제 하강 거리·삭제 줄 수·동작 직전 레벨에 따른 음이 아닌 점수 계산. */
@FunctionalInterface
public interface ScoreRule {
    long scoreFor(int droppedCells, int clearedLines, int levelBeforeStep);
}
