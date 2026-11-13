package team.tetris.application;

import team.tetris.application.model.GameMode;
import team.tetris.application.network.P2pLobby;

/** UI에 제공하는 서비스와 새 게임 생성 경계. */
public interface ApplicationContext {
    SettingsService settings();
    ScoreboardService scores();
    EndGameCoordinator endings();

    /** mode로 새 게임 시작. 모드는 결과·기록·순위 구분까지 유지된다. */
    StartedGame newGame(GameMode mode);

    default P2pLobby newP2pLobby() {
        throw new UnsupportedOperationException("P2P is not available");
    }

    default boolean debugEnabled() { return false; }

    default StartedGame newDebugGame(team.tetris.application.debug.DebugSetup setup, GameMode mode) {
        throw new IllegalStateException("Debug mode is not enabled");
    }

    /**
     * 한 컴퓨터에서 두 명이 하는 대전 시작 (3차 카드 17). 두 플레이어에게 설정의 난이도를 똑같이 적용하고
     * (안건 ⑯) 기본 블록은 같은 순서로 나온다(안건 ⑰). 결과는 스코어보드에 기록하지 않는다.
     */
    default team.tetris.application.battle.StartedBattle newLocalBattle(GameMode mode) {
        throw new UnsupportedOperationException("Local battle is not available");
    }

    /** 일반 모드로 새 게임 시작. */
    default StartedGame newGame() {
        return newGame(GameMode.NORMAL);
    }
}
