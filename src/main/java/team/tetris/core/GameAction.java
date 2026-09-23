package team.tetris.core;

/**
 * PlayerEngine.apply(GameAction)로 전달하는 조작 명령. 키 입력을 어떤 물리 키에 매핑할지는
 * UI(InputMapper)의 책임이고, core는 이 enum만 안다.
 */
public enum GameAction {
    MOVE_LEFT,
    MOVE_RIGHT,
    SOFT_DROP,
    HARD_DROP,
    ROTATE_CW,
    ROTATE_CCW,
    PAUSE,
    RESUME
}
