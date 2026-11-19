package team.tetris.network;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.OptionalLong;
import java.util.function.LongSupplier;
import team.tetris.application.network.NetworkPolicy;

/** peer의 시각을 신뢰하지 않고 로컬 단조 시계만 사용한다. 미응답·중복 pong은 표본이 아니다. */
final class PingTracker {
    private final LongSupplier clock;
    private final Map<Long, Long> pending = new LinkedHashMap<>();
    private OptionalLong latest = OptionalLong.empty();
    private long lastResponse;

    PingTracker(LongSupplier clock) { this.clock = clock; }

    synchronized void connected() {
        lastResponse = clock.getAsLong();
        pending.clear();
        latest = OptionalLong.empty();
    }

    synchronized void sent(long id) {
        if (pending.size() == 4) pending.remove(pending.keySet().iterator().next());
        pending.put(id, clock.getAsLong());
    }

    synchronized void received(long id) {
        Long start = pending.remove(id);
        if (start != null) {
            long now = clock.getAsLong();
            latest = OptionalLong.of(Math.max(0, now - start));
            lastResponse = now;
            // 최신 응답보다 오래된 미응답이 회복 표시를 계속 막지 않도록 한다.
            pending.keySet().removeIf(previous -> previous < id);
        }
    }

    synchronized OptionalLong latest() { return latest; }

    synchronized boolean lagging() {
        long now = clock.getAsLong();
        return latest.orElse(0) > NetworkPolicy.LAG_NANOS
                || pending.values().stream().anyMatch(start -> now - start > NetworkPolicy.LAG_NANOS);
    }

    synchronized boolean timedOut() {
        return clock.getAsLong() - lastResponse >= NetworkPolicy.DISCONNECT_NANOS;
    }
}
