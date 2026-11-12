package team.tetris.network;

import static org.junit.jupiter.api.Assertions.*;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

class PingTrackerTest {
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
