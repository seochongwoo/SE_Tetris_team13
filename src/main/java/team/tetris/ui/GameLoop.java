package team.tetris.ui;

import java.util.Objects;
import java.util.function.LongConsumer;
import javax.swing.Timer;
import team.tetris.ui.input.NanoClock;

/**
 * 프레임 루프. Swing 타이머로 약 60fps마다 깨어나, 직전 프레임 이후 흐른 시간을 단조 시계로 재서
 * frameHandler에 넘긴다. 타이머와 키 이벤트가 모두 Swing 이벤트 스레드에서 돌기 때문에 세션 호출이
 * 한 스레드에서 직렬로 이뤄진다는 계약이 자연히 지켜진다.
 *
 * <p>다른 화면에 있거나 일시정지 중에도 직전 시각은 매 프레임 갱신된다. 그래서 게임 화면으로
 * 돌아왔을 때 그동안의 시간이 한꺼번에 흘러가지 않는다.
 */
public final class GameLoop {

    public static final int FRAME_MILLIS = 16;

    private final NanoClock clock;
    private final LongConsumer frameHandler;
    private long previous;
    private boolean running;
    private Timer timer;

    public GameLoop(NanoClock clock, LongConsumer frameHandler) {
        this.clock = Objects.requireNonNull(clock, "clock");
        this.frameHandler = Objects.requireNonNull(frameHandler, "frameHandler");
    }

    public void start() {
        if (running) {
            return;
        }
        begin();
        timer = new Timer(FRAME_MILLIS, event -> tick());
        timer.setCoalesce(true);
        timer.start();
    }

    /** 타이머 없이 루프 상태만 시작한다 (테스트에서 tick을 직접 부를 때). */
    void begin() {
        previous = clock.nanoTime();
        running = true;
    }

    /** 한 프레임: 경과 시간을 계산해 넘긴다. */
    void tick() {
        if (!running) {
            return;
        }
        long now = clock.nanoTime();
        long elapsed = Math.max(0L, now - previous);
        previous = now;
        frameHandler.accept(elapsed);
    }

    public void stop() {
        running = false;
        if (timer != null) {
            timer.stop();
        }
    }

    public boolean isRunning() {
        return running;
    }
}
