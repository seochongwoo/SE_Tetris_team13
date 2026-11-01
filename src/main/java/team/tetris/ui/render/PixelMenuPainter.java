package team.tetris.ui.render;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

/**
 * 메인 메뉴의 그림(하늘·별·구름·대성당·로고·메뉴 패널·선택 막대·커서)을 도트 그래픽으로 그린다.
 *
 * <p>모든 그림을 화면보다 정수 배 작은 가상 캔버스 하나에 안티앨리어싱 없이 그린 뒤, 보간 없이 정수 배로만
 * 확대한다. 그래서 로고·구름·대성당의 도트 크기가 같고 번지지 않는다. 글자는 그 위에 {@link TextFramePanel}이
 * 따로 그린다. 색은 해 질 녘 톤의 작은 팔레트만 쓰고, 움직임(별 반짝임, 구름, 커서, 로고 광택)은 모두 도트 한
 * 칸 단위로 끊어 움직인다.
 */
final class PixelMenuPainter {

    /** 메뉴 배치와 맞춘 행: 부제 아래 경계와 대성당이 서는 땅. {@code MenuScreen}의 배치를 바꾸면 함께 바꾼다. */
    static final int SUBTITLE_BOTTOM_ROW = 7;
    static final int GROUND_ROW = 21;
    /** 대성당 가운데 열. 메뉴 패널이 오른쪽에 있으므로 왼쪽 절반 가운데에 세운다. */
    static final int CATHEDRAL_COLUMN = 12;

    /** 하늘: 위에서 지평선 쪽으로. */
    private static final Color[] SKY = {
        rgb(0x0B0F2E), rgb(0x151A4A), rgb(0x23266B), rgb(0x3A2F82), rgb(0x5C3A8E), rgb(0x8A4A93)
    };
    private static final Color INK = rgb(0x06081A);
    private static final Color SILHOUETTE = rgb(0x1A1740);
    private static final Color SILHOUETTE_FRONT = rgb(0x241F55);
    private static final Color LATTICE = rgb(0x2E2766);
    private static final Color RIM = rgb(0xC0647A);
    private static final Color LIGHT = rgb(0xF2C14E);
    private static final Color LIGHT_TOP = rgb(0xFFE59A);
    private static final Color STAR_DIM = rgb(0x6A6FB0);
    private static final Color STAR_BRIGHT = rgb(0xE0E0FF);
    private static final Color CLOUD = rgb(0x2E2A72);
    private static final Color CLOUD_TOP = rgb(0x4A3A8A);
    private static final Color PANEL = rgb(0x0E1236);
    private static final Color PANEL_LINE = rgb(0x5C3A8E);
    /** 돔 색 3종: [바탕, 줄무늬·그늘]. */
    private static final Color[][] DOMES = {
        {rgb(0xC0647A), rgb(0x7A3A5E)}, {rgb(0xD9A441), rgb(0x8A5A20)}, {rgb(0x4F8FA0), rgb(0x2A4F66)}
    };
    /** 로고 블록 색을 하늘과 어울리게 채도를 낮출 때 섞는 색. */
    private static final Color LOGO_MUTE = new Color(110, 110, 140);

    private static final long CLOUD_STEP_NANOS = 200_000_000L;
    private static final long TWINKLE_NANOS = 500_000_000L;
    private static final long CURSOR_NANOS = 400_000_000L;
    private static final long SHINE_PERIOD_NANOS = 4_000_000_000L;
    private static final long SHINE_NANOS = 800_000_000L;
    private static final int STARS = 24;
    /** 구름: 부제 아래에서의 높이(도트), 너비(도트), 시작 위치(너비 %), 0.2초마다 움직이는 도트 수. */
    private static final int[][] CLOUDS = {{3, 30, 10, 1}, {11, 40, 55, 1}, {6, 22, 85, 2}};

    private PixelMenuPainter() {
    }

    /** 화면 크기 프리셋별 정수 배율: 글자 칸 높이 9px당 1배, 최소 2배. */
    static int scaleFor(int cellHeight) {
        return Math.max(2, Math.round(cellHeight / 9f));
    }

    /** 격자 칸 좌표를 가상 캔버스 도트 좌표로 바꾼다. */
    private record Grid(int cellWidth, int cellHeight, int scale) {
        int x(int col) {
            return col * cellWidth / scale;
        }

        int y(int row) {
            return row * cellHeight / scale;
        }

        Rectangle cells(int col, int row, int width, int height) {
            return new Rectangle(x(col), y(row), x(col + width) - x(col), y(row + height) - y(row));
        }
    }

    static void paint(Graphics2D g, TextFrame frame, int width, int height, int cellWidth, int cellHeight) {
        int scale = scaleFor(cellHeight);
        int w = Math.max(1, width / scale);
        int h = Math.max(1, height / scale);
        BufferedImage image = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D canvas = image.createGraphics();
        try {
            canvas.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
            Grid grid = new Grid(cellWidth, cellHeight, scale);
            long time = frame.backdrop().timeNanos();
            int ground = Math.min(h, grid.y(GROUND_ROW));
            int skyTop = grid.y(SUBTITLE_BOTTOM_ROW);
            paintSky(canvas, w, h, ground, time);
            paintClouds(canvas, w, skyTop, time);
            paintCathedral(canvas, grid.x(CATHEDRAL_COLUMN), ground, Math.max(1, (ground - skyTop - 6) / 42));
            paintLogo(canvas, frame, grid, time);
            for (Rectangle plate : frame.plates()) {
                paintPanel(canvas, grid.cells(plate.x, plate.y, plate.width, plate.height));
            }
            paintBars(canvas, frame, grid);
            paintCursors(canvas, frame, grid, time);
        } finally {
            canvas.dispose();
        }
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        g.drawImage(image, 0, 0, w * scale, h * scale, null);
    }

    // ---- 하늘 ----

    /** 같은 높이의 띠 6개, 띠 경계는 2×2 격자로 섞는다. 위쪽 절반에는 별이 반짝인다. */
    private static void paintSky(Graphics2D g, int w, int h, int ground, long time) {
        for (int i = 0; i < SKY.length; i++) {
            int top = ground * i / SKY.length;
            int bottom = ground * (i + 1) / SKY.length;
            g.setColor(SKY[i]);
            g.fillRect(0, top, w, bottom - top);
        }
        for (int i = 1; i < SKY.length; i++) {
            int y = ground * i / SKY.length;
            for (int x = 0; x < w; x++) {
                g.setColor(x % 2 == 0 ? SKY[i] : SKY[i - 1]);
                g.fillRect(x, x % 2 == 0 ? y - 1 : y, 1, 1);
            }
        }
        g.setColor(SILHOUETTE);
        g.fillRect(0, ground, w, h - ground);
        long phase = time / TWINKLE_NANOS;
        int starHeight = Math.max(1, ground / 2);
        for (int i = 0; i < STARS; i++) {
            int x = (i * 73 + 11) % w;
            int y = (i * 41 + 3) % starHeight;
            boolean bright = (phase + i) % 4 == 0;
            g.setColor(bright ? STAR_BRIGHT : STAR_DIM);
            g.fillRect(x, y, 1, 1);
            if (bright && i % 3 == 0) {
                g.setColor(STAR_DIM);
                g.fillRect(x - 1, y, 1, 1);
                g.fillRect(x + 1, y, 1, 1);
                g.fillRect(x, y - 1, 1, 1);
                g.fillRect(x, y + 1, 1, 1);
            }
        }
    }

    /** 구름은 0.2초마다 도트 단위로 왼쪽으로 흐르고, 화면을 벗어나면 오른쪽에서 다시 들어온다. */
    private static void paintClouds(Graphics2D g, int w, int top, long time) {
        long step = time / CLOUD_STEP_NANOS;
        for (int[] cloud : CLOUDS) {
            int width = cloud[1];
            int span = w + width;
            int x = (int) Math.floorMod(cloud[2] * w / 100 - step * cloud[3], (long) span) - width;
            int y = top + cloud[0];
            g.setColor(CLOUD_TOP);
            fillCloud(g, x, y, width);
            Shape clip = g.getClip();
            g.clipRect(x, y + width / 6, width, width);
            g.setColor(CLOUD);
            fillCloud(g, x, y, width);
            g.setClip(clip);
        }
    }

    private static void fillCloud(Graphics2D g, int x, int y, int width) {
        g.fillOval(x, y + width / 8, width / 2, width / 4);
        g.fillOval(x + width / 4, y, width / 2, width / 3);
        g.fillOval(x + width / 2, y + width / 10, width / 2, width / 4);
        g.fillRect(x + width / 8, y + width / 6, width * 3 / 4, width / 8 + 1);
    }

    // ---- 대성당 ----

    /** 대성당 좌표(단위, 위쪽이 +)를 도트 좌표로 바꾼다. */
    private record Place(int centerX, int groundY, int unit) {
        int x(float ux) {
            return centerX + Math.round(ux * unit);
        }

        int y(float uy) {
            return groundY - Math.round(uy * unit);
        }
    }

    /** 본체는 거의 실루엣으로 두고, 왼쪽 가장자리에만 노을빛을, 창문에는 불빛을 넣는다. 돔 색은 3종. */
    private static void paintCathedral(Graphics2D g, int centerX, int ground, int unit) {
        Place p = new Place(centerX, ground, unit);
        for (int side : new int[] {-1, 1}) {
            paintTower(g, p, side * 16, 3f, 7, 15, 4.2f, 4.6f, SILHOUETTE, DOMES[2]);
        }
        for (int side : new int[] {-1, 1}) {
            paintTower(g, p, side * 10, 5f, 9, 21, 7f, 7.2f, SILHOUETTE, DOMES[0]);
        }
        paintCentralTower(g, p);
        for (int side : new int[] {-1, 1}) {
            paintTower(g, p, side * 5, 4f, 8, 23, 5.6f, 6f, SILHOUETTE_FRONT, DOMES[1]);
        }
        paintBase(g, p);
    }

    private static void paintTower(Graphics2D g, Place p, float x, float drumWidth, float bottom, float top,
            float domeWidth, float domeHeight, Color body, Color[] dome) {
        paintDrum(g, p, x, drumWidth, bottom, top, body);
        paintOnion(g, p, x, top, domeWidth, domeHeight, dome);
    }

    private static void paintCentralTower(Graphics2D g, Place p) {
        paintDrum(g, p, 0, 5.6f, 10, 26, SILHOUETTE);
        Path2D.Float roof = new Path2D.Float();
        roof.moveTo(p.x(-2.8f), p.y(26));
        roof.lineTo(p.x(2.8f), p.y(26));
        roof.lineTo(p.x(0.45f), p.y(37));
        roof.lineTo(p.x(-0.45f), p.y(37));
        roof.closePath();
        g.setColor(SILHOUETTE_FRONT);
        g.fill(roof);
        Shape clip = g.getClip();
        g.clip(roof);
        g.setColor(LATTICE);
        for (float k = -12; k <= 12; k += 2f) {
            g.draw(new Line2D.Float(p.x(k - 6), p.y(26), p.x(k + 6), p.y(37)));
            g.draw(new Line2D.Float(p.x(k + 6), p.y(26), p.x(k - 6), p.y(37)));
        }
        g.setClip(clip);
        g.setColor(RIM);
        g.draw(new Line2D.Float(p.x(-2.8f), p.y(26), p.x(-0.45f), p.y(37)));
        paintDrum(g, p, 0, 1.6f, 37, 38.5f, SILHOUETTE);
        paintOnion(g, p, 0, 38.5f, 2.6f, 2.8f, DOMES[1]);
    }

    /** 탑 몸통: 실루엣에 왼쪽 가장자리 노을빛과 작은 불 켜진 창문. */
    private static void paintDrum(Graphics2D g, Place p, float x, float width, float bottom, float top, Color body) {
        int left = p.x(x - width / 2);
        int right = p.x(x + width / 2);
        int y = p.y(top);
        int height = p.y(bottom) - y;
        g.setColor(body);
        g.fillRect(left, y, right - left, height);
        g.setColor(RIM);
        g.fillRect(left, y, 1, height);
        if (top - bottom < 3) {
            return;
        }
        g.fillRect(left, y, right - left, 1);
        int windows = Math.max(1, Math.round(width / 2));
        g.setColor(LIGHT);
        for (float row = top - 3; row > bottom + 1.5f; row -= 5f) {
            for (int i = 0; i < windows; i++) {
                int wx = p.x(x - width / 2 + width * (i + 0.5f) / windows);
                g.fillRect(wx, p.y(row), 1, Math.max(1, p.unit() * 2 / 3));
            }
        }
    }

    /** 양파 돔: 바탕색에 비틀린 줄무늬, 오른쪽은 그늘, 테두리는 가장 어두운 색, 꼭대기는 금색 첨탑. */
    private static void paintOnion(Graphics2D g, Place p, float x, float base, float width, float height,
            Color[] colors) {
        int cx = p.x(x);
        int bottom = p.y(base);
        float w = width * p.unit();
        float h = height * p.unit();
        float top = bottom - h;
        Path2D.Float dome = new Path2D.Float();
        dome.moveTo(cx - w * 0.30f, bottom);
        dome.curveTo(cx - w * 0.66f, bottom - h * 0.22f, cx - w * 0.46f, bottom - h * 0.74f, cx, top);
        dome.curveTo(cx + w * 0.46f, bottom - h * 0.74f, cx + w * 0.66f, bottom - h * 0.22f, cx + w * 0.30f, bottom);
        dome.closePath();
        g.setColor(colors[0]);
        g.fill(dome);
        Shape clip = g.getClip();
        g.clip(dome);
        g.setColor(colors[1]);
        float band = Math.max(1f, w / 6);
        for (float bx = cx - w * 1.2f; bx < cx + w; bx += 2 * band) {
            Path2D.Float twist = new Path2D.Float();
            twist.moveTo(bx, bottom);
            twist.lineTo(bx + band, bottom);
            twist.lineTo(bx + band + w * 0.45f, top);
            twist.lineTo(bx + w * 0.45f, top);
            twist.closePath();
            g.fill(twist);
        }
        g.fillRect(Math.round(cx + w * 0.18f), Math.round(top), Math.round(w), Math.round(h) + 1);
        g.setClip(clip);
        g.setColor(INK);
        g.draw(dome);
        g.setColor(LIGHT);
        int spire = Math.max(2, Math.round(h * 0.3f));
        g.fillRect(cx, Math.round(top) - spire, 1, spire);
        g.fillRect(cx - 1, Math.round(top) - spire / 2 - 1, 2, 2);
    }

    /** 아래 성벽: 실루엣, 위쪽 노을빛 한 줄, 어두운 아치 회랑. */
    private static void paintBase(Graphics2D g, Place p) {
        int left = p.x(-19);
        int right = p.x(19);
        int top = p.y(8);
        g.setColor(SILHOUETTE);
        g.fillRect(left, top, right - left, p.groundY() - top);
        g.setColor(RIM);
        g.fillRect(left, top, right - left, 1);
        g.setColor(INK);
        int archWidth = Math.max(2, p.unit());
        for (float x = -17.5f; x <= 17.5f; x += 2.5f) {
            g.fillRect(p.x(x) - archWidth / 2, p.y(5.5f), archWidth, p.y(1) - p.y(5.5f));
        }
    }

    // ---- 로고와 메뉴 ----

    /**
     * 로고(█ 칸): 2도트 어두운 그림자 위에, 채도를 낮춘 블록 색으로 칠하고 위·왼쪽은 밝게, 아래·오른쪽은
     * 어둡게 1도트씩 테두리를 준다. 몇 초마다 대각선 광택이 지나간다.
     */
    private static void paintLogo(Graphics2D g, TextFrame frame, Grid grid, long time) {
        List<Rectangle> blocks = new ArrayList<>();
        List<Color> colors = new ArrayList<>();
        for (int row = 0; row < frame.rows(); row++) {
            for (int col = 0; col < frame.columns(); col++) {
                if (frame.codePointAt(col, row) == '█') {
                    blocks.add(grid.cells(col, row, 1, 1));
                    colors.add(TilePainter.mix(frame.styleAt(col, row).foreground(), LOGO_MUTE, 0.25f));
                }
            }
        }
        if (blocks.isEmpty()) {
            return;
        }
        g.setColor(INK);
        for (Rectangle block : blocks) {
            g.fillRect(block.x + 2, block.y + 2, block.width, block.height);
        }
        Rectangle bounds = new Rectangle(blocks.get(0));
        for (int i = 0; i < blocks.size(); i++) {
            Rectangle b = blocks.get(i);
            Color color = colors.get(i);
            g.setColor(color);
            g.fillRect(b.x, b.y, b.width, b.height);
            g.setColor(TilePainter.mix(color, Color.WHITE, 0.35f));
            g.fillRect(b.x, b.y, b.width, 1);
            g.fillRect(b.x, b.y, 1, b.height);
            g.setColor(TilePainter.mix(color, Color.BLACK, 0.4f));
            g.fillRect(b.x, b.y + b.height - 1, b.width, 1);
            g.fillRect(b.x + b.width - 1, b.y, 1, b.height);
            bounds.add(b);
        }
        long phase = time % SHINE_PERIOD_NANOS;
        if (phase >= SHINE_NANOS) {
            return;
        }
        long sweep = phase * (bounds.width + bounds.height) / SHINE_NANOS;
        g.setColor(LIGHT_TOP);
        for (Rectangle b : blocks) {
            for (int y = b.y; y < b.y + b.height; y++) {
                for (int x = b.x; x < b.x + b.width; x++) {
                    long d = (x - bounds.x) + (y - bounds.y) - sweep;
                    if (d >= 0 && d < 2) {
                        g.fillRect(x, y, 1, 1);
                    }
                }
            }
        }
    }

    /** 도트 패널: 바깥 1도트 가장 어두운 테두리, 안쪽 1도트 밝은 선, 단색 채움. */
    private static void paintPanel(Graphics2D g, Rectangle r) {
        g.setColor(PANEL);
        g.fillRect(r.x, r.y, r.width, r.height);
        g.setColor(INK);
        g.drawRect(r.x, r.y, r.width - 1, r.height - 1);
        g.setColor(PANEL_LINE);
        g.drawRect(r.x + 1, r.y + 1, r.width - 3, r.height - 3);
    }

    /** 선택 막대 등 배경색 구간: 윗줄 밝게, 아랫줄 어둡게 3톤으로 칠하고 네 모서리를 1도트씩 깎는다. */
    private static void paintBars(Graphics2D g, TextFrame frame, Grid grid) {
        Color base = frame.styleAt(0, 0).background();
        for (int row = 0; row < frame.rows(); row++) {
            int col = 0;
            while (col < frame.columns()) {
                Color color = frame.styleAt(col, row).background();
                if (color.equals(base) || frame.tileAt(col, row) != null || frame.isTileContinuation(col, row)) {
                    col++;
                    continue;
                }
                int end = col + 1;
                while (end < frame.columns() && color.equals(frame.styleAt(end, row).background())) {
                    end++;
                }
                Rectangle r = grid.cells(col, row, end - col, 1);
                int top = r.y + 1;
                int height = Math.max(3, r.height - 2);
                g.setColor(color);
                g.fillRect(r.x, top + 1, r.width, height - 2);
                g.setColor(TilePainter.mix(color, Color.WHITE, 0.45f));
                g.fillRect(r.x + 1, top, r.width - 2, 1);
                g.setColor(TilePainter.mix(color, Color.BLACK, 0.3f));
                g.fillRect(r.x + 1, top + height - 1, r.width - 2, 1);
                col = end;
            }
        }
    }

    /** 선택 표시(►) 자리에 작은 T블록을 그리고, 1도트씩 위아래로 까딱이게 한다. */
    private static void paintCursors(Graphics2D g, TextFrame frame, Grid grid, long time) {
        int bob = (int) ((time / CURSOR_NANOS) % 2);
        for (int row = 0; row < frame.rows(); row++) {
            for (int col = 0; col < frame.columns(); col++) {
                if (frame.codePointAt(col, row) != '►') {
                    continue;
                }
                Rectangle cell = grid.cells(col, row, 1, 1);
                int block = Math.max(1, cell.height / 5);
                int x = cell.x + (cell.width - 3 * block) / 2;
                int y = cell.y + (cell.height - 2 * block) / 2 + bob;
                g.setColor(SILHOUETTE);
                g.fillRect(x, y, 3 * block, block);
                g.fillRect(x + block, y + block, block, block);
                g.setColor(PANEL_LINE);
                g.fillRect(x, y, 3 * block, 1);
            }
        }
    }

    private static Color rgb(int value) {
        return new Color(value);
    }
}
