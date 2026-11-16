package team.tetris.application.battle;

/**
 * 시간제한 대전의 시계 (3차 R3-05, 안건 ⑪). 일시정지 중에는 줄지 않는다 (안건 ⑱).
 *
 * @param limitNanos     처음 정한 시간
 * @param remainingNanos 남은 시간 (0이면 끝남)
 */
public record BattleTimer(long limitNanos, long remainingNanos) {

    public BattleTimer {
        if (limitNanos <= 0) {
            throw new IllegalArgumentException("Time limit must be positive");
        }
        if (remainingNanos < 0 || remainingNanos > limitNanos) {
            throw new IllegalArgumentException("Remaining time must be within the limit");
        }
    }

    public boolean expired() {
        return remainingNanos == 0;
    }

    /** 화면에 보여 줄 남은 초. 1초 미만이 남아도 끝나기 전까지는 1초로 올려 보여 준다. */
    public long remainingSeconds() {
        return (remainingNanos + 999_999_999L) / 1_000_000_000L;
    }
}
