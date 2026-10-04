package team.tetris.application;

import java.util.Optional;

/**
 * 한 판의 입력과 시간 처리. 모든 메서드는 하나의 게임 루프 스레드에서 직렬 호출.
 * UI는 단조 증가 시계로 측정한 경과 시간 전달 및 정지 중에도 직전 시각 갱신.
 */
public interface GameSession {
    void handle(GameCommand command);
    void update(long elapsedNanos);
    GameSnapshot snapshot();

    /** 진행 중에는 empty, 종료 후에는 한 번 확정된 동일 결과 반환. */
    Optional<GameResult> result();
}
