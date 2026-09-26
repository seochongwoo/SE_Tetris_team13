package team.tetris.application.port;

import team.tetris.application.ApplicationContext;

/** UI 시작 경계. 게임 루프의 호출 순서·스레드·화면 전환은 UI 책임. */
@FunctionalInterface
public interface GameUi {
    void open(ApplicationContext application);
}
