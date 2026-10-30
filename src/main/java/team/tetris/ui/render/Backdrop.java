package team.tetris.ui.render;

import java.awt.Rectangle;
import java.util.Objects;

/**
 * 화면 뒤에 깔리는 움직이는 배경. 화면은 어떤 배경을 어느 시점으로 그릴지만 정하고, 실제 그림은
 * {@link TextFramePanel}이 픽셀 단위로 그린다. 그래서 움직임은 부드럽고 화면 로직은 Swing 없이 테스트할 수 있다.
 *
 * @param timeNanos 배경이 시작된 뒤 흐른 시간. 구름·조각의 위치를 정한다
 * @param level 게임 배경의 색을 정하는 레벨. 메뉴 배경은 0
 * @param hole 배경을 그리지 않을 영역(격자 칸 단위). 없으면 null
 */
public record Backdrop(Scene scene, long timeNanos, int level, Rectangle hole) {

    public enum Scene {
        /** 메인 메뉴: 블록으로 쌓은 성은 그대로 있고 구름만 흘러간다. */
        CASTLE,
        /** 게임 화면: 보드 바깥으로 작은 블록 조각이 천천히 떠오른다. */
        DRIFT
    }

    public Backdrop {
        Objects.requireNonNull(scene, "scene");
        hole = hole == null ? null : new Rectangle(hole);
    }

    @Override
    public Rectangle hole() {
        return hole == null ? null : new Rectangle(hole);
    }

    public static Backdrop castle(long timeNanos) {
        return new Backdrop(Scene.CASTLE, timeNanos, 0, null);
    }

    public static Backdrop drift(long timeNanos, int level, Rectangle hole) {
        return new Backdrop(Scene.DRIFT, timeNanos, level, hole);
    }
}
