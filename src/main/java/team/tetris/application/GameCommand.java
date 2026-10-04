package team.tetris.application;

/** UI가 큐 순서대로 전달하는 한 번의 명령. 키 반복은 UI 책임. */
public enum GameCommand {
    MOVE_LEFT, MOVE_RIGHT, SOFT_DROP, HARD_DROP, ROTATE_CW,
    PAUSE, RESUME, QUIT_GAME
}
