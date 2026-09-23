package team.tetris.core;

/**
 * PlayerEngine의 현재 상태.
 *
 * READY: 아직 첫 블록이 스폰되기 전 (현재 구현에서는 생성자가 즉시 다음 상태로 넘기므로
 * 외부에서 관찰되지는 않지만, 추후 확장을 위해 남겨둔다).
 * RUNNING: 정상 진행 중, 조작/자동 하강이 적용됨.
 * PAUSED: 일시정지 - tick()과 PAUSE/RESUME을 제외한 조작이 전부 무시됨.
 * GAME_OVER: 더 이상 블록을 스폰할 수 없어 게임이 끝남 - 이후 모든 조작이 무시됨.
 */
public enum EnginePhase {
    READY,
    RUNNING,
    PAUSED,
    GAME_OVER
}
