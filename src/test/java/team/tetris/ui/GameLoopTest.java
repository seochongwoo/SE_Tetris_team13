package team.tetris.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

class GameLoopTest {

    private final AtomicLong now = new AtomicLong(1_000);
    private final List<Long> frames = new ArrayList<>();
    private final GameLoop loop = new GameLoop(now::get, frames::add);

    @Test
    void eachTickReportsTheTimeSinceThePreviousTick() {
        loop.begin();
        now.addAndGet(16);
        loop.tick();
        now.addAndGet(20);
        loop.tick();

        assertEquals(List.of(16L, 20L), frames);
        assertTrue(loop.isRunning());
    }

    @Test
    void clockGoingBackwardsNeverProducesNegativeTime() {
        loop.begin();
        now.addAndGet(-50);
        loop.tick();

        assertEquals(List.of(0L), frames);
    }

    @Test
    void stoppedLoopIgnoresTicks() {
        loop.begin();
        loop.stop();
        now.addAndGet(16);
        loop.tick();

        assertTrue(frames.isEmpty());
        assertFalse(loop.isRunning());
    }
}
