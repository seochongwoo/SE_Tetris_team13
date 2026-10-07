package team.tetris.ui.input;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class RepeatControllerTest {

    private static final long DELAY = 100;
    private static final long INTERVAL = 30;
    private final RepeatController repeats = new RepeatController(DELAY, INTERVAL);

    @Test
    void secondPressWithoutReleaseIsTreatedAsOsAutoRepeat() {
        assertTrue(repeats.press("LEFT", 0, true));
        assertFalse(repeats.press("LEFT", 5, true));
        assertTrue(repeats.isHeld("LEFT"));

        repeats.release("LEFT");

        assertFalse(repeats.isHeld("LEFT"));
        assertTrue(repeats.press("LEFT", 10, true));
    }

    @Test
    void repeatableKeyFiresAfterInitialDelayThenEveryInterval() {
        repeats.press("LEFT", 0, true);

        assertEquals(List.of(), repeats.due(DELAY - 1));
        assertEquals(List.of("LEFT"), repeats.due(DELAY));
        assertEquals(List.of(), repeats.due(DELAY + INTERVAL - 1));
        assertEquals(List.of("LEFT"), repeats.due(DELAY + INTERVAL));
    }

    @Test
    void nonRepeatableKeyNeverRepeats() {
        repeats.press("SPACE", 0, false);

        assertEquals(List.of(), repeats.due(10_000));
    }

    @Test
    void releasingStopsRepeating() {
        repeats.press("LEFT", 0, true);
        repeats.press("DOWN", 0, true);
        repeats.release("LEFT");

        assertEquals(List.of("DOWN"), repeats.due(DELAY));

        repeats.releaseAll();

        assertEquals(List.of(), repeats.due(DELAY + INTERVAL));
    }

    @Test
    void longStallDoesNotFloodRepeats() {
        repeats.press("LEFT", 0, true);

        assertEquals(5, repeats.due(DELAY + 100 * INTERVAL).size());
        assertEquals(List.of(), repeats.due(DELAY + 100 * INTERVAL));
    }

    @Test
    void rejectsNonPositiveTimings() {
        assertThrows(IllegalArgumentException.class, () -> new RepeatController(0, 10));
        assertThrows(IllegalArgumentException.class, () -> new RepeatController(10, 0));
    }
}
