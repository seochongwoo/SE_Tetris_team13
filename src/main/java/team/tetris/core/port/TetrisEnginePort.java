package team.tetris.core.port;

import team.tetris.core.EngineSnapshot;
import team.tetris.core.GameAction;
import team.tetris.core.result.EngineStep;

/**
 * application이 core를 호출하는 driving port. core가 정의하고, core의 PlayerEngine이
 * 구현한다. application(GameSession 등)은 이 인터페이스 타입으로만 core를 참조해야 하며,
 * PlayerEngine 구체 클래스를 직접 알아서는 안 된다.
 *
 * 실제 조립 PlayerEngine은 애플리케이션 어디서나가 아니라
 *  bootstrap/AppComposition.java(컴포지션 루트) 한 곳에서만 일어나고, 그 결과를
 * 이 타입으로 application 쪽에 주입한다.
 */
public interface TetrisEnginePort {

    /**
     * 현재 상태의 읽기 전용 스냅샷.
     */
    EngineSnapshot snapshot();

    /**
     * 키 입력 등 사용자 조작 하나를 적용한다.
     */
    EngineStep apply(GameAction action);

    /**
     * 중력에 의한 자동 하강 1회분. application의 게임 루프가 일정 주기로 호출한다.
     */
    EngineStep tick();
}
