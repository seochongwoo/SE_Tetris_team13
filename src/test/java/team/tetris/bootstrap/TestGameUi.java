package team.tetris.bootstrap;

import team.tetris.application.ApplicationContext;
import team.tetris.application.port.GameUi;

/** main의 ServiceLoader 연결 확인용 테스트 전용 UI. */
public final class TestGameUi implements GameUi {
    static ApplicationContext application;
    @Override
    public void open(ApplicationContext context) { application = context; }
}
