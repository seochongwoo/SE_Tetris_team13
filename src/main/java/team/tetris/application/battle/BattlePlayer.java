package team.tetris.application.battle;

/** 대전의 두 플레이어. 로컬 대전에서 1P는 왼쪽 보드, 2P는 오른쪽 보드다. */
public enum BattlePlayer {
    ONE, TWO;

    /** 상대 플레이어. */
    public BattlePlayer opponent() {
        return this == ONE ? TWO : ONE;
    }
}
