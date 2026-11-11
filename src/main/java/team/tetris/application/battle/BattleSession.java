package team.tetris.application.battle;

import java.util.Optional;
import team.tetris.application.GameCommand;

/**
 * 두 플레이어 대전 한 판. 화면(카드 19)은 이 인터페이스만 쓰고, 로컬 대전과 P2P 대전(카드 24)이 각각
 * 구현한다. 모든 메서드는 하나의 게임 루프 스레드에서 호출한다.
 */
public interface BattleSession {

    /**
     * player의 조작 하나를 적용한다. PAUSE·RESUME은 누가 눌러도 두 보드에 함께 적용하고(안건 ⑱),
     * QUIT_GAME은 그 플레이어가 나간 것으로 보아 상대가 이긴다. 끝난 뒤에는 무시한다.
     */
    void handle(BattlePlayer player, GameCommand command);

    /** 승패 없이 대전을 중단한다 (공통 메뉴에서 나가기). 끝난 뒤에는 무시한다. */
    void abort();

    /** 경과 시간만큼 두 보드를 진행한다. 정지 중이거나 끝난 뒤에는 무시한다. */
    void update(long elapsedNanos);

    BattleSnapshot snapshot();

    /** 진행 중이면 empty, 끝나면 한 번 확정된 같은 결과. */
    Optional<BattleResult> result();
}
