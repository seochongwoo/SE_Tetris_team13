package team.tetris.application;

import team.tetris.application.model.GameMode;

/** UI에 제공하는 서비스와 새 게임 생성 경계. */
public interface ApplicationContext {
    SettingsService settings();
    ScoreboardService scores();
    EndGameCoordinator endings();

    /** mode로 새 게임 시작. 모드는 결과·기록·순위 구분까지 유지된다. */
    StartedGame newGame(GameMode mode);

    /** 일반 모드로 새 게임 시작. */
    default StartedGame newGame() {
        return newGame(GameMode.NORMAL);
    }
}
