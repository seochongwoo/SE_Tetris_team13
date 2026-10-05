package team.tetris.ui.input;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 키를 누르고 있을 때의 반복 입력을 만든다.
 *
 * <ul>
 *   <li>처음 누른 순간은 호출하는 쪽이 바로 처리하고, 이 클래스는 그 뒤의 반복만 만든다.</li>
 *   <li>OS 자동 반복으로 같은 키가 떼어지지 않은 채 다시 눌리면 무시한다 ({@link #press}가 false).</li>
 *   <li>반복 가능한 키만 initialDelay 뒤부터 interval마다 다시 발생시킨다.</li>
 * </ul>
 *
 * <p>OS 자동 반복은 첫 반복까지 지연이 길고 속도도 PC마다 달라서, 반복을 직접 만들어 어느 PC에서나
 * 같은 반응을 보장한다.
 */
public final class RepeatController {

    public static final long DEFAULT_INITIAL_DELAY_NANOS = 170_000_000L;
    public static final long DEFAULT_INTERVAL_NANOS = 50_000_000L;

    private static final int MAX_REPEATS_PER_POLL = 5;
    private static final long NOT_REPEATING = Long.MAX_VALUE;

    private final long initialDelayNanos;
    private final long intervalNanos;
    private final Map<String, Long> nextFire = new LinkedHashMap<>();

    public RepeatController() {
        this(DEFAULT_INITIAL_DELAY_NANOS, DEFAULT_INTERVAL_NANOS);
    }

    public RepeatController(long initialDelayNanos, long intervalNanos) {
        if (initialDelayNanos <= 0 || intervalNanos <= 0) {
            throw new IllegalArgumentException("Repeat timings must be positive");
        }
        this.initialDelayNanos = initialDelayNanos;
        this.intervalNanos = intervalNanos;
    }

    /**
     * key가 눌렸음을 기록한다. 이미 눌린 상태면(OS 자동 반복) false를 반환하고 아무것도 하지 않는다.
     */
    public boolean press(String key, long nowNanos, boolean repeatable) {
        if (nextFire.containsKey(key)) {
            return false;
        }
        nextFire.put(key, repeatable ? nowNanos + initialDelayNanos : NOT_REPEATING);
        return true;
    }

    public void release(String key) {
        nextFire.remove(key);
    }

    public void releaseAll() {
        nextFire.clear();
    }

    public boolean isHeld(String key) {
        return nextFire.containsKey(key);
    }

    /**
     * nowNanos까지 발생해야 할 반복 입력을 순서대로 반환한다. 루프가 오래 멈췄다 돌아와도 한 번에
     * 쏟아지지 않도록 키당 최대 횟수를 두고, 그보다 밀린 만큼은 버리고 다음 주기부터 이어간다.
     */
    public List<String> due(long nowNanos) {
        List<String> fired = new ArrayList<>();
        for (Map.Entry<String, Long> entry : nextFire.entrySet()) {
            long next = entry.getValue();
            if (next == NOT_REPEATING) {
                continue;
            }
            int count = 0;
            while (nowNanos >= next && count < MAX_REPEATS_PER_POLL) {
                fired.add(entry.getKey());
                next += intervalNanos;
                count++;
            }
            if (nowNanos >= next) {
                next = nowNanos + intervalNanos;
            }
            entry.setValue(next);
        }
        return fired;
    }
}
