package team.tetris.network;

import static org.junit.jupiter.api.Assertions.*;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

class PingTrackerTest {
    @Test
    void lagBoundaryIncludesUnansweredPingAndFastResponseRecovers() {
        var clock = new AtomicLong();
        var tracker = new PingTracker(clock::get);
        tracker.connected();
        assertFalse(tracker.lagging());
        tracker.sent(1);
        clock.set(200_000_000L);
        assertFalse(tracker.lagging());
        clock.incrementAndGet();
        assertTrue(tracker.lagging());
        tracker.received(1);
        assertTrue(tracker.lagging());
        clock.set(1_000_000_000L); tracker.sent(2);
        clock.addAndGet(200_000_000L); tracker.received(2);
        assertFalse(tracker.lagging());
        assertFalse(tracker.timedOut());
        clock.addAndGet(2_999_999_999L);
        assertFalse(tracker.timedOut());
        clock.incrementAndGet();
        assertTrue(tracker.timedOut());
    }

    @Test
    void noFirstPongTimesOutAtThreeSecondsAndUnsolicitedOrDuplicatePongCannotKeepAlive() {
        var clock = new AtomicLong(-10_000_000_000L); // nanoTime의 기준점은 음수여도 된다.
        var tracker = new PingTracker(clock::get);
        tracker.connected(); tracker.sent(1);
        clock.addAndGet(2_999_999_999L); tracker.received(99);
        assertFalse(tracker.timedOut());
        clock.incrementAndGet();
        assertTrue(tracker.timedOut());
        tracker.connected(); tracker.sent(2); tracker.received(2);
        clock.addAndGet(3_000_000_000L); tracker.received(2);
        assertTrue(tracker.timedOut());
    }

    @Test
    void newPongRetiresOlderPendingRequestsAndReconnectClearsMeasurements() {
        var clock = new AtomicLong();
        var tracker = new PingTracker(clock::get);
        tracker.connected(); tracker.sent(1);
        clock.set(1_000_000_000L); tracker.sent(2);
        clock.addAndGet(10_000_000L); tracker.received(2);
        assertFalse(tracker.lagging());
        tracker.received(1);
        assertEquals(10_000_000L, tracker.latest().orElseThrow());
        tracker.connected();
        assertTrue(tracker.latest().isEmpty());
        assertFalse(tracker.lagging());
        assertFalse(tracker.timedOut());
    }

    @Test
    void measuresLocalElapsedTimeAndIgnoresUnsolicitedRepeatedOrExpiredPong() {
        var clock = new AtomicLong(1_000);
        var tracker = new PingTracker(clock::get);
        assertTrue(tracker.latest().isEmpty());
        tracker.received(9);
        assertTrue(tracker.latest().isEmpty());
        tracker.sent(1); clock.addAndGet(200_000_001); tracker.received(1);
        assertEquals(200_000_001, tracker.latest().orElseThrow());
        clock.addAndGet(10); tracker.received(1);
        assertEquals(200_000_001, tracker.latest().orElseThrow());
        for (int id = 2; id <= 6; id++) tracker.sent(id);
        tracker.received(2); // 최대 네 개만 보관하므로 만료됨.
        assertEquals(200_000_001, tracker.latest().orElseThrow());
        clock.addAndGet(150_000_000); tracker.received(6);
        assertEquals(150_000_000, tracker.latest().orElseThrow());
    }
}
