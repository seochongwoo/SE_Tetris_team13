package team.tetris.ui.input;

import java.awt.Canvas;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class KeyReleaseFilterTest {
    private KeyEvent event(int type, long when) {
        return new KeyEvent(new Canvas(), type, when, 0, KeyEvent.VK_LEFT, KeyEvent.CHAR_UNDEFINED);
    }

    @Test void linuxRepeatDoesNotReleaseHeldKey() {
        var tasks = new ArrayList<Runnable>();
        var events = new ArrayList<String>();
        var filter = new KeyReleaseFilter(tasks::add, e -> events.add("press"), e -> events.add("release"));
        filter.press(event(KeyEvent.KEY_PRESSED, 1));
        filter.release(event(KeyEvent.KEY_RELEASED, 100));
        filter.press(event(KeyEvent.KEY_PRESSED, 100));
        tasks.forEach(Runnable::run);
        assertEquals(List.of("press"), events);
        filter.release(event(KeyEvent.KEY_RELEASED, 150));
        tasks.forEach(Runnable::run);
        assertEquals(List.of("press", "release"), events);
    }

    @Test void genuineRapidTapIsPreserved() {
        var tasks = new ArrayList<Runnable>();
        var events = new ArrayList<String>();
        var filter = new KeyReleaseFilter(tasks::add, e -> events.add("press"), e -> events.add("release"));
        filter.press(event(KeyEvent.KEY_PRESSED, 1));
        filter.release(event(KeyEvent.KEY_RELEASED, 100));
        filter.press(event(KeyEvent.KEY_PRESSED, 101));
        tasks.forEach(Runnable::run);
        assertEquals(List.of("press", "release", "press"), events);
    }
}
