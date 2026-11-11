package team.tetris.application.battle;

/** 대전 전체의 진행 상태. 한 명이 정지하면 둘 다 정지한다 (3차 안건 ⑱). */
public enum BattleStatus {
    RUNNING, PAUSED, FINISHED
}
