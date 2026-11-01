package team.tetris.ui.render;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.geom.Area;
import java.awt.geom.RoundRectangle2D;

/**
 * 게임 화면의 {@link Backdrop}(떠오르는 블록 조각)을 그린다. 메인 메뉴의 대성당 장면은 글자 격자의 로고·패널과
 * 함께 도트로 그려야 하므로 {@link PixelMenuPainter}가 맡는다.
 *
 * <p>모든 위치는 시간만으로 계산하므로 같은 시점이면 언제나 같은 그림이 나온다.
 */
final class BackdropPainter {

    /** 블록 조각 색: 테트로미노 기본 색. */
    private static final Color[] PIECES = {
        new Color(0, 200, 220), new Color(230, 210, 0), new Color(200, 0, 200), new Color(0, 200, 60),
        new Color(220, 40, 40), new Color(60, 110, 255), new Color(240, 130, 0)
    };
    private static final int PARTICLES = 28;

    private BackdropPainter() {
    }

    static void paint(Graphics2D g, Backdrop backdrop, int width, int height, int cellWidth, int cellHeight) {
        if (backdrop.scene() != Backdrop.Scene.DRIFT) {
            return;
        }
        Graphics2D layer = (Graphics2D) g.create();
        try {
            Rectangle hole = backdrop.hole();
            if (hole != null) {
                Area area = new Area(new Rectangle(0, 0, width, height));
                area.subtract(new Area(new Rectangle(hole.x * cellWidth, hole.y * cellHeight,
                        hole.width * cellWidth, hole.height * cellHeight)));
                layer.clip(area);
            }
            paintDrift(layer, backdrop.timeNanos(), backdrop.level(), width, height, cellHeight / 2f);
        } finally {
            layer.dispose();
        }
    }

    /** 작은 블록 조각이 아래에서 위로 천천히 떠오른다. 레벨마다 색 조합이 바뀐다. */
    private static void paintDrift(Graphics2D g, long timeNanos, int level, int width, int height, float unit) {
        double seconds = timeNanos / 1e9;
        for (int i = 0; i < PARTICLES; i++) {
            float size = unit * (0.5f + (i * 37 % 10) / 10f);
            float speed = unit * (0.6f + (i * 53 % 10) / 10f);
            float x = (i * 97 % 100) / 100f * width;
            float travel = height + 2 * size;
            float y = (float) (height + size - ((i * 61 % 100) / 100f * travel + seconds * speed) % travel);
            Color color = PIECES[Math.floorMod(i + level, PIECES.length)];
            g.setColor(new Color(color.getRed(), color.getGreen(), color.getBlue(), 22 + (i * 29 % 4) * 6));
            g.fill(new RoundRectangle2D.Float(x, y, size, size, size / 3, size / 3));
        }
    }
}
