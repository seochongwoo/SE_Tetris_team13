package team.tetris.application.battle;

import java.util.Objects;
import java.util.Optional;

/**
 * 한 번 확정된 대전 결과. 대전 결과는 스코어보드에 기록하지 않는다 (3차 R3-13).
 *
 * @param winner 이긴 플레이어. 무승부나 중단이면 null
 * @param end    끝난 이유
 * @param scoreOne 1P의 최종 점수
 * @param scoreTwo 2P의 최종 점수
 */
public record BattleResult(BattlePlayer winner, BattleEnd end, long scoreOne, long scoreTwo) {

    public BattleResult {
        Objects.requireNonNull(end, "end");
        boolean hasWinner = end == BattleEnd.TOPPED_OUT || end == BattleEnd.FORFEIT;
        if (hasWinner != (winner != null)) {
            throw new IllegalArgumentException("Winner must be given exactly when " + end + " decides one");
        }
    }

    public Optional<BattlePlayer> winnerIfAny() {
        return Optional.ofNullable(winner);
    }

    /** 같은 프레임 동시 패배로 무승부인가. */
    public boolean draw() {
        return end == BattleEnd.BOTH_TOPPED_OUT;
    }

    public long scoreOf(BattlePlayer player) {
        return player == BattlePlayer.ONE ? scoreOne : scoreTwo;
    }
}
