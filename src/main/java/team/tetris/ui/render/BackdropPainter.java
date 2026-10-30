package team.tetris.ui.render;

import java.awt.Color;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.geom.Area;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;

/**
 * {@link Backdrop}을 그린다. 모든 위치는 시간만으로 계산하므로 같은 시점이면 언제나 같은 그림이 나온다.
 *
 * <p>성 배경은 블록 색 벽돌로 쌓은 성을 바닥에 두고, 하늘에는 구름이 오른쪽에서 왼쪽으로 흘러간다.
 * 구름은 성 뒤로 지나가며, 전체를 배경색 쪽으로 어둡게 섞어 메뉴 글자를 가리지 않게 한다.
 */
final class BackdropPainter {

    /**
     * 성 모양. 한 글자가 정사각 단위 하나(글자 칸 높이의 절반). # 벽돌, w 창문, d 문, p 깃대, f 깃발.
     */
    static final String[] CASTLE = {
        ".....................pfff...................",
        ".....................pff....................",
        ".....................p......................",
        "....................####....................",
        "....................####....................",
        "....................####....................",
        "......pfff......##.#####.##..........pfff...",
        "......pff.......############.........pff....",
        "......p.........############.........p......",
        "......p.........#####ww#####.........p......",
        "...##.##.##.....##ww#ww#ww##.....##.##.##...",
        "...########.....##ww####ww##.....########...",
        "...########.....############.....########...",
        "...###ww###.....############.....###ww###...",
        "...###ww###.....##ww####ww##.....###ww###...",
        "...##########.####ww####ww####.##########...",
        "...######################################...",
        "...###ww############################ww###...",
        "##.###ww#############dd#############ww#####.",
        "####################dddd####################",
        "####################dddd####################",
        "####################dddd####################",
        "####################dddd####################",
        "####################dddd####################"
    };
    /** 벽돌 색: 테트로미노 기본 색을 어둡게 섞어 쓴다. */
    private static final Color[] BRICKS = {
        new Color(0, 200, 220), new Color(230, 210, 0), new Color(200, 0, 200), new Color(0, 200, 60),
        new Color(220, 40, 40), new Color(60, 110, 255), new Color(240, 130, 0)
    };
    /** 성을 배경색 쪽으로 섞는 비율. 클수록 어둡다. */
    private static final float CASTLE_DIM = 0.82f;
    private static final Color WINDOW = new Color(255, 214, 120);
    private static final Color FLAG = new Color(230, 60, 60);
    private static final Color SKY_LOW = new Color(38, 30, 66);

    /** 구름: 높이(화면 높이 비율), 크기(단위 수), 속도(초당 단위 수), 시작 위치(화면 너비 비율). */
    private static final float[][] CLOUDS = {
        {0.08f, 7f, 0.55f, 0.10f},
        {0.20f, 10f, 0.35f, 0.55f},
        {0.31f, 6f, 0.75f, 0.85f},
        {0.14f, 5f, 0.95f, 0.35f},
        {0.40f, 8f, 0.45f, 0.20f}
    };

    private static final int PARTICLES = 28;

    private BackdropPainter() {
    }

    static void paint(Graphics2D g, Backdrop backdrop, int width, int height, int cellWidth, int cellHeight,
            Color background) {
        Graphics2D layer = (Graphics2D) g.create();
        try {
            Rectangle hole = backdrop.hole();
            if (hole != null) {
                Area area = new Area(new Rectangle(0, 0, width, height));
                area.subtract(new Area(new Rectangle(hole.x * cellWidth, hole.y * cellHeight,
                        hole.width * cellWidth, hole.height * cellHeight)));
                layer.clip(area);
            }
            switch (backdrop.scene()) {
                case CASTLE -> paintCastleScene(layer, backdrop.timeNanos(), width, height, cellHeight / 2f, background);
                case DRIFT -> paintDrift(layer, backdrop.timeNanos(), backdrop.level(), width, height, cellHeight / 2f);
            }
        } finally {
            layer.dispose();
        }
    }

    private static void paintCastleScene(Graphics2D g, long timeNanos, int width, int height, float unit,
            Color background) {
        g.setPaint(new GradientPaint(0, 0, background, 0, height, TilePainter.mix(background, SKY_LOW, 0.9f)));
        g.fillRect(0, 0, width, height);
        double seconds = timeNanos / 1e9;
        for (float[] cloud : CLOUDS) {
            paintCloud(g, seconds, cloud, width, height, unit);
        }
        int columns = CASTLE[0].length();
        float left = (width - columns * unit) / 2f;
        float top = height - CASTLE.length * unit;
        for (int y = 0; y < CASTLE.length; y++) {
            for (int x = 0; x < columns; x++) {
                char c = CASTLE[y].charAt(x);
                if (c == '.') {
                    continue;
                }
                float px = left + x * unit;
                float py = top + y * unit;
                switch (c) {
                    case '#' -> paintBrick(g, x, y, px, py, unit, background);
                    case 'w' -> fillCell(g, px, py, unit, TilePainter.mix(WINDOW, background, 0.7f));
                    case 'd' -> fillCell(g, px, py, unit, TilePainter.mix(new Color(20, 14, 30), background, 0.3f));
                    case 'p' -> {
                        g.setColor(TilePainter.mix(Color.LIGHT_GRAY, background, CASTLE_DIM));
                        g.fill(new Rectangle2D.Float(px + unit * 0.4f, py, unit * 0.2f, unit));
                    }
                    case 'f' -> fillCell(g, px, py, unit, TilePainter.mix(FLAG, background, 0.65f));
                    default -> throw new IllegalStateException("Unknown castle cell: " + c);
                }
            }
        }
    }

    /** 벽돌은 가로 두 단위이고 줄마다 반 칸씩 엇갈린다. 색은 위치로 정해 늘 같다. */
    private static void paintBrick(Graphics2D g, int x, int y, float px, float py, float unit, Color background) {
        int brick = (x + (y % 2)) / 2;
        int index = Math.floorMod(brick * 7 + y * 3 + (brick * y) % 5, BRICKS.length);
        Color color = TilePainter.mix(BRICKS[index], background, CASTLE_DIM);
        boolean leftEdge = (x + (y % 2)) % 2 == 0;
        float gap = Math.max(0.5f, unit / 10f);
        g.setColor(color);
        g.fill(new Rectangle2D.Float(px + (leftEdge ? gap : 0), py + gap, unit - (leftEdge ? gap : 0), unit - gap));
        g.setColor(TilePainter.mix(color, Color.WHITE, 0.08f));
        g.fill(new Rectangle2D.Float(px + (leftEdge ? gap : 0), py + gap, unit - (leftEdge ? gap : 0), gap));
    }

    private static void fillCell(Graphics2D g, float px, float py, float unit, Color color) {
        g.setColor(color);
        g.fill(new Rectangle2D.Float(px, py, unit, unit));
    }

    /** 둥근 덩어리 몇 개를 겹친 구름. 화면을 벗어나면 반대쪽에서 다시 들어온다. */
    private static void paintCloud(Graphics2D g, double seconds, float[] cloud, int width, int height, float unit) {
        float size = cloud[1] * unit;
        float span = width + 2 * size;
        float x = (float) (cloud[3] * span - seconds * cloud[2] * unit);
        x = (float) (((x % span) + span) % span) - size;
        float y = cloud[0] * height;
        Area shape = new Area(new RoundRectangle2D.Float(x, y + size * 0.25f, size * 2f, size * 0.45f,
                size * 0.45f, size * 0.45f));
        shape.add(new Area(new Ellipse2D.Float(x + size * 0.35f, y, size * 0.8f, size * 0.6f)));
        shape.add(new Area(new Ellipse2D.Float(x + size * 0.9f, y + size * 0.08f, size * 0.7f, size * 0.55f)));
        g.setColor(new Color(220, 225, 255, 30));
        g.fill(shape);
    }

    /** 게임 배경: 작은 블록 조각이 아래에서 위로 천천히 떠오른다. 레벨마다 색이 바뀐다. */
    private static void paintDrift(Graphics2D g, long timeNanos, int level, int width, int height, float unit) {
        double seconds = timeNanos / 1e9;
        for (int i = 0; i < PARTICLES; i++) {
            float size = unit * (0.5f + (i * 37 % 10) / 10f);
            float speed = unit * (0.6f + (i * 53 % 10) / 10f);
            float x = (i * 97 % 100) / 100f * width;
            float travel = height + 2 * size;
            float y = (float) (height + size - ((i * 61 % 100) / 100f * travel + seconds * speed) % travel);
            Color color = BRICKS[Math.floorMod(i + level, BRICKS.length)];
            g.setColor(new Color(color.getRed(), color.getGreen(), color.getBlue(), 22 + (i * 29 % 4) * 6));
            g.fill(new RoundRectangle2D.Float(x, y, size, size, size / 3, size / 3));
        }
    }
}
