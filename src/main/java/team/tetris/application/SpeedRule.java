package team.tetris.application;

/** 누적 삭제 줄 수에 따른 음이 아닌 레벨과 양수 낙하 간격 계산. */
public interface SpeedRule {
    int levelFor(int totalClearedLines);
    long gravityIntervalNanos(int level);
}
