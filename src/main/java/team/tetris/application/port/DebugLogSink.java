package team.tetris.application.port;

/** 디버그 로그 출력 경계. 출력 실패는 description에 알리고 게임 진행은 유지한다. */
public interface DebugLogSink {
    void append(String entry);
    String description();
}
