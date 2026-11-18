package team.tetris.ui.input;

import java.awt.event.KeyEvent;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

/** Linux의 자동 반복 해제/눌림 쌍을 하나의 유지된 눌림으로 전달한다. EDT 전용. */
public final class KeyReleaseFilter {
    private final Map<Integer, KeyEvent> pending = new HashMap<>();
    private final Consumer<Runnable> defer;
    private final Consumer<KeyEvent> pressed;
    private final Consumer<KeyEvent> released;

    public KeyReleaseFilter(Consumer<Runnable> defer, Consumer<KeyEvent> pressed,
                            Consumer<KeyEvent> released) {
        this.defer = defer;
        this.pressed = pressed;
        this.released = released;
    }

    public void press(KeyEvent event) {
        KeyEvent release = pending.remove(event.getKeyCode());
        if (release != null) {
            if (release.getWhen() == event.getWhen()) return;
            released.accept(release);
        }
        pressed.accept(event);
    }

    public void release(KeyEvent event) {
        KeyEvent previous = pending.put(event.getKeyCode(), event);
        if (previous != null) released.accept(previous);
        defer.accept(() -> {
            if (pending.remove(event.getKeyCode(), event)) released.accept(event);
        });
    }
}
