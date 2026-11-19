package team.tetris.network;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.OptionalLong;
import java.util.function.LongSupplier;

/** peer의 시각을 신뢰하지 않고 로컬 단조 시계만 사용한다. 미응답·중복 pong은 표본이 아니다. */
final class PingTracker {
    private final LongSupplier clock;
    private final Map<Long, Long> pending = new LinkedHashMap<>();
    private OptionalLong latest = OptionalLong.empty();

    PingTracker(LongSupplier clock) { this.clock = clock; }

    synchronized void sent(long id) {
        if (pending.size() == 4) pending.remove(pending.keySet().iterator().next());
        pending.put(id, clock.getAsLong());
    }

    synchronized void received(long id) {
        Long start = pending.remove(id);
        if (start != null) latest = OptionalLong.of(Math.max(0, clock.getAsLong() - start));
    }

    synchronized OptionalLong latest() { return latest; }
}
