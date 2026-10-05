package team.tetris.ui.input;

/**
 * 단조 증가 시계. 게임 루프와 키 반복 처리는 이 인터페이스로만 시간을 읽으므로, 테스트에서는
 * 가짜 시계를 넣어 시간 흐름을 직접 조절할 수 있다. 시스템 날짜·시각은 쓰지 않는다.
 */
@FunctionalInterface
public interface NanoClock {

    long nanoTime();

    static NanoClock system() {
        return System::nanoTime;
    }
}
