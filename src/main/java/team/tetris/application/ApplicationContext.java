package team.tetris.application;

/** UI에 제공하는 서비스와 새 게임 생성 경계. */
public interface ApplicationContext {
    SettingsService settings();
    ScoreboardService scores();
    EndGameCoordinator endings();
    StartedGame newGame();
}
