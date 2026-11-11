package team.tetris.application.battle;

/** 대전이 끝난 이유. */
public enum BattleEnd {
    /** 한 명의 보드가 더 이상 쌓을 수 없게 되어 상대가 이김 (공격 줄에 밀려 넘친 경우 포함, 안건 ⑩). */
    TOPPED_OUT,
    /** 같은 프레임에 두 명 모두 쌓을 수 없게 되어 무승부 (안건 ⑬). */
    BOTH_TOPPED_OUT,
    /** 한 명이 나가서 상대가 이김 (안건 ⑱). */
    FORFEIT,
    /** 승패 없이 중단 (로컬 대전에서 공통 메뉴로 나감). */
    ABORTED
}
