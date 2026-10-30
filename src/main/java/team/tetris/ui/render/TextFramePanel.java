package team.tetris.ui.render;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.font.TextLayout;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import javax.swing.JComponent;
import team.tetris.application.model.Settings.ScreenSize;
import team.tetris.ui.render.palette.BlockGlyphs;

/**
 * {@link TextFrame}을 그리는 Swing 컴포넌트. 화면 크기 설정은 글자 크기만 바꾸고, 격자 크기(보드 10×20
 * 포함)는 그대로다.
 *
 * <p>글자는 영문 고정폭 글꼴(Consolas)로, 그 글꼴에 없는 한글 등은 맑은 고딕으로 그린다. 블록 칸
 * ({@link BlockTile})과 상자 테두리, 선택 줄 배경은 글자 대신 도형으로 그린다.
 *
 * <p>각 글자를 자기 칸의 절대 위치에 찍기 때문에, 글꼴에서 한글 폭이 정확히 두 칸이 아니어도
 * 오차가 옆 칸으로 누적되지 않는다.
 *
 * <p>줄 높이는 글꼴의 기본 줄 간격({@code getHeight()}) 대신 글자 크기의 {@value #LINE_HEIGHT_RATIO}배로
 * 잡는다. 기본 줄 간격은 한글 대체 글꼴 때문에 글자 크기의 1.5배가 넘어, 창이 노트북 화면(1080p, 배율
 * 150%)보다 커지고 테두리 선도 끊겨 보였다.
 */
public final class TextFramePanel extends JComponent {

    static final float LINE_HEIGHT_RATIO = 1.1f;
    static final int MIN_FONT_SIZE = 10;
    /** 영문·숫자용 글꼴 후보. 설치된 첫 글꼴을 쓰고, 없으면 Java 기본 고정폭 글꼴을 쓴다. */
    private static final List<String> LATIN_FAMILIES = List.of("Consolas", "Cascadia Mono");
    /** 한글용 글꼴 후보. */
    private static final List<String> KOREAN_FAMILIES = List.of("Malgun Gothic", "맑은 고딕");
    /** 움직이는 배경 위에서 글자가 읽히도록 글자 아래에 까는 그림자 색. */
    private static final Color TEXT_SHADOW = new Color(0, 0, 0, 170);
    private static final int[][] SHADOW_OFFSETS = {{-1, 0}, {1, 0}, {0, -1}, {0, 1}, {1, 1}, {2, 2}};
    /** 배경 그림 위에 까는 판의 색. 흰 글자 대비가 4.5:1 이상이 되도록 어둡게 덮는다. */
    private static final Color PLATE = new Color(0, 0, 0, 150);
    /** 한글 글꼴에서 가장 위아래로 긴 글자들. 이 범위가 줄 안에 들어가게 크기와 기준선을 잡는다. */
    static final String KOREAN_EXTENT_SAMPLE = "한읽움뷁[]";
    static final String LATIN_EXTENT_SAMPLE = "H[]gy|";
    /** 한글 문구에 잇지 않고 영문 글꼴로 따로 그리는 괄호. 맑은 고딕의 괄호는 아래로 길어 줄 밖으로 나간다. */
    private static final String BRACKETS = "[]()";
    /** 일시정지·게임 종료 때 뒤 화면을 어둡게 덮는 색. */
    private static final Color SHADE = new Color(0, 0, 0, 165);
    private static Set<String> installedFamilies;

    private TextFrame frame;
    /** 영문, 한글, 기본 글꼴 순서. 각각 [보통, 굵게]. */
    private Font[][] fonts;
    /** 글꼴별 기준선. 글꼴마다 잉크 높이가 달라 각자 칸 가운데에 오도록 따로 잡는다. */
    private final Map<Font, Integer> baselines = new HashMap<>();
    private int fontSize;
    private int cellWidth;
    private int cellHeight;
    private boolean textInput;

    public TextFramePanel(ScreenSize size, TextFrame initial) {
        this.frame = Objects.requireNonNull(initial, "initial");
        setFocusable(true);
        setFocusTraversalKeysEnabled(false);
        setOpaque(true);
        // 기본은 입력기를 끈다. 한글 모드에서도 글자 키 눌림이 게임 조작으로 들어오게 하기 위해서다.
        enableInputMethods(false);
        setScreenSize(size);
    }

    /** 화면 크기 설정별 글자 크기(px). */
    public static int fontSizeFor(ScreenSize size) {
        return switch (size) {
            case SMALL -> 14;
            case MEDIUM -> 18;
            case LARGE -> 24;
        };
    }

    public void setScreenSize(ScreenSize size) {
        setScreenSize(size, null);
    }

    /**
     * 화면 크기를 적용한다. limit(이 패널이 차지할 수 있는 최대 크기)이 주어지고 설정 크기로는
     * 넘치면, 들어갈 때까지 글자 크기를 줄인다 (최소 {@value #MIN_FONT_SIZE}px).
     */
    public void setScreenSize(ScreenSize size, Dimension limit) {
        int chosen = fontSizeFor(size);
        while (limit != null && chosen > MIN_FONT_SIZE && !fits(sizeFor(chosen), limit)) {
            chosen--;
        }
        applyFontSize(chosen);
        revalidate();
        repaint();
    }

    /** 실제로 쓰는 글자 크기(px). 화면이 작으면 설정값보다 작을 수 있다. */
    public int fontSize() {
        return fontSize;
    }

    /**
     * OS 입력기를 켜거나 끈다. 한글 이름처럼 조합 문자를 받는 동안만 켠다. 다시 켜면 입력기는 영문
     * 상태로 시작하므로 사용자가 한/영 키로 바꿔 입력한다.
     */
    public void setTextInput(boolean enabled) {
        if (enabled != textInput) {
            textInput = enabled;
            enableInputMethods(enabled);
        }
    }

    public boolean isTextInput() {
        return textInput;
    }

    public void show(TextFrame next) {
        frame = Objects.requireNonNull(next, "next");
        repaint();
    }

    @Override
    public Dimension getPreferredSize() {
        return new Dimension(frame.columns() * cellWidth, frame.rows() * cellHeight);
    }

    private Dimension sizeFor(int size) {
        Font font = new Font(family(LATIN_FAMILIES), Font.PLAIN, size);
        return new Dimension(frame.columns() * getFontMetrics(font).charWidth('M'),
                frame.rows() * lineHeight(size));
    }

    /** 줄 높이. 작은 글자에서도 괄호·아래로 긴 글자가 위아래 1px 여유 안에 들어가도록 글자 크기 + 2px 이상. */
    private static int lineHeight(int size) {
        return Math.max(Math.round(size * LINE_HEIGHT_RATIO), size + 2);
    }

    private static boolean fits(Dimension size, Dimension limit) {
        return size.width <= limit.width && size.height <= limit.height;
    }

    private void applyFontSize(int size) {
        fontSize = size;
        String[] families = {family(LATIN_FAMILIES), family(KOREAN_FAMILIES), Font.MONOSPACED};
        // 기준선을 잡는 글자: 가장 위아래로 긴 글자와 괄호까지 포함해, 어떤 글자도 줄 밖으로 나가지 않게 한다.
        String[] samples = {LATIN_EXTENT_SAMPLE, KOREAN_EXTENT_SAMPLE, LATIN_EXTENT_SAMPLE};
        fonts = new Font[families.length][];
        baselines.clear();
        cellHeight = lineHeight(size);
        cellWidth = getFontMetrics(new Font(families[0], Font.PLAIN, size)).charWidth('M');
        for (int i = 0; i < families.length; i++) {
            Font plain = new Font(families[i], Font.PLAIN, size);
            if (i == 1) {
                plain = plain.deriveFont(koreanFontSize(plain, size));
            }
            fonts[i] = new Font[] {plain, plain.deriveFont(Font.BOLD)};
            for (Font font : fonts[i]) {
                Rectangle ink = inkOf(samples[i], font);
                // 잉크 위쪽은 기준선 위 -ink.y, 높이는 ink.height. 남는 공간을 위아래로 나눈다.
                baselines.put(font, Math.round((cellHeight - ink.height) / 2f) - ink.y);
            }
        }
    }

    /**
     * 한글 글꼴 크기. 영문보다 좁게 그려지면 한 글자가 두 칸을 거의 채우도록 키우되, 받침 있는 글자와 괄호가
     * 줄 높이(위아래 1px 여유) 안에 들어가도록 줄인다. 줄 높이가 글자 크기의 1.1배로 빠듯해서, 그대로 두면
     * 글자 아래가 선택 막대 밖으로 나가 잘려 보인다.
     */
    private float koreanFontSize(Font plain, int size) {
        float advance = getFontMetrics(plain).charWidth('한');
        float scale = Math.max(1f, Math.min(1.15f, 2 * cellWidth * 0.92f / advance));
        float limit = (cellHeight - 2f) / inkOf(KOREAN_EXTENT_SAMPLE, plain.deriveFont(Font.BOLD)).height;
        return size * Math.min(scale, limit);
    }

    private Rectangle inkOf(String text, Font font) {
        return new TextLayout(text, font, getFontMetrics(font).getFontRenderContext()).getPixelBounds(null, 0, 0);
    }

    /** codePoint를 그릴 수 있는 첫 글꼴. */
    private Font fontFor(int codePoint, boolean bold) {
        for (Font[] family : fonts) {
            if (family[0].canDisplay(codePoint)) {
                return family[bold ? 1 : 0];
            }
        }
        return fonts[fonts.length - 1][bold ? 1 : 0];
    }

    private static synchronized String family(List<String> candidates) {
        if (installedFamilies == null) {
            installedFamilies = Set.copyOf(List.of(
                    GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames()));
        }
        return candidates.stream().filter(installedFamilies::contains).findFirst().orElse(Font.MONOSPACED);
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        Graphics2D g = (Graphics2D) graphics.create();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
            TextFrame current = frame;
            Color base = current.styleAt(0, 0).background();
            g.setColor(base);
            g.fillRect(0, 0, getWidth(), getHeight());
            boolean pixelMenu = current.backdrop() != null
                    && current.backdrop().scene() == Backdrop.Scene.CATHEDRAL;
            if (pixelMenu) {
                // 메인 메뉴는 그림 요소(배경·로고·패널·선택 막대)를 한 도트 캔버스에 그린다.
                PixelMenuPainter.paint(g, current, getWidth(), getHeight(), cellWidth, cellHeight);
            } else {
                if (current.backdrop() != null) {
                    BackdropPainter.paint(g, current.backdrop(), getWidth(), getHeight(), cellWidth, cellHeight);
                }
                g.setColor(PLATE);
                for (Rectangle plate : current.plates()) {
                    g.fillRect(plate.x * cellWidth, plate.y * cellHeight, plate.width * cellWidth,
                            plate.height * cellHeight);
                }
                paintBackgrounds(g, current, base);
            }
            for (int row = 0; row < current.rows(); row++) {
                for (int col = 0; col < current.columns(); col++) {
                    BlockTile tile = current.tileAt(col, row);
                    if (tile != null) {
                        TilePainter.paint(g, tile, col * cellWidth, row * cellHeight, 2 * cellWidth, cellHeight,
                                base, fontFor('A', true));
                    }
                }
            }
            paintGlyphs(g, current, pixelMenu);
            g.setColor(SHADE);
            for (int row = 0; row < current.rows(); row++) {
                for (int col = 0; col < current.columns(); col++) {
                    if (current.isShaded(col, row)) {
                        g.fillRect(col * cellWidth, row * cellHeight, cellWidth, cellHeight);
                    }
                }
            }
        } finally {
            g.dispose();
        }
    }

    /**
     * 기본 배경과 다른 배경색(선택 줄, 삭제 강조 등)을 칠한다. 한 줄에서 같은 색이 이어지는 구간을
     * 막대 하나로 칠해 칸 경계가 보이지 않게 한다. 도트 배경과 어울리도록 모서리는 직각이다.
     */
    private void paintBackgrounds(Graphics2D g, TextFrame current, Color base) {
        for (int row = 0; row < current.rows(); row++) {
            int col = 0;
            while (col < current.columns()) {
                Color color = backgroundAt(current, col, row);
                if (color == null || color.equals(base)) {
                    col++;
                    continue;
                }
                int end = col + 1;
                while (end < current.columns() && color.equals(backgroundAt(current, end, row))) {
                    end++;
                }
                g.setColor(color);
                g.fillRect(col * cellWidth, row * cellHeight + 1, (end - col) * cellWidth, cellHeight - 2);
                col = end;
            }
        }
    }

    /** 타일이 덮은 칸은 타일이 직접 그리므로 배경 칠하기에서 뺀다. */
    private static Color backgroundAt(TextFrame current, int col, int row) {
        if (current.tileAt(col, row) != null || current.isTileContinuation(col, row)) {
            return null;
        }
        return current.styleAt(col, row).background();
    }

    /** pixelMenu이면 도트 캔버스가 이미 그린 로고 칸(█)과 선택 표시(►)는 건너뛴다. */
    private void paintGlyphs(Graphics2D g, TextFrame current, boolean pixelMenu) {
        Map<Font, Rectangle2D> markerBounds = new HashMap<>(); // 같은 프레임에서는 글꼴별로 한 번만 측정한다.
        for (int row = 0; row < current.rows(); row++) {
            for (int col = 0; col < current.columns(); col++) {
                if (current.isContinuation(col, row) || current.tileAt(col, row) != null
                        || current.isTileContinuation(col, row)) {
                    continue;
                }
                int codePoint = current.codePointAt(col, row);
                if (codePoint == ' ') {
                    continue;
                }
                TextStyle style = current.styleAt(col, row);
                int x = col * cellWidth;
                int y = row * cellHeight;
                g.setColor(style.foreground());
                if (paintLine(g, codePoint, x, y)) {
                    continue;
                }
                if (pixelMenu && (codePoint == '█' || codePoint == '►')) {
                    continue;
                }
                if (codePoint == '█') {
                    TilePainter.paintPixel(g, style.foreground(), x, y, cellWidth, cellHeight);
                    continue;
                }
                Font font = fontFor(codePoint, style.bold());
                g.setFont(font);
                String glyph = new String(Character.toChars(codePoint));
                if (codePoint == '・') {
                    // 빈 칸 표시는 전각 두 칸의 중심에 잉크를 맞춘다. 글꼴별 기준선 차이도 보정한다.
                    Rectangle2D ink = markerBounds.computeIfAbsent(font,
                            f -> new TextLayout(BlockGlyphs.EMPTY, f, g.getFontRenderContext()).getBounds());
                    // 논리 좌표로 배치해 HiDPI 변환이 적용돼도 칸 중심을 유지한다.
                    float markerX = (float) (x + (2 * cellWidth - ink.getWidth()) / 2 - ink.getX());
                    float markerY = (float) (y + (cellHeight - ink.getHeight()) / 2 - ink.getY());
                    g.drawString(glyph, markerX, markerY);
                    continue;
                }
                int span = TextFrame.isWide(codePoint) ? 2 : 1;
                if (codePoint == '[' || codePoint == '(') {
                    // 여는 괄호는 칸 오른쪽에 붙여 뒤 글자와 벌어지지 않게 한다.
                    x += cellWidth - g.getFontMetrics().stringWidth(glyph);
                } else {
                    // 첫 글자를 자기 칸 가운데에 맞춘다. 글꼴이 달라도 글자 위치가 칸을 벗어나지 않는다.
                    x += Math.round((span * cellWidth - g.getFontMetrics().stringWidth(glyph)) / 2f);
                }
                int closing = -1;
                if (span == 2 && font.equals(fontFor('한', style.bold()))) {
                    // 한글로 시작하는 문구는 한 칸 띄어쓰기까지 이어서 한 번에 그린다. 격자에서는 공백도 한 칸이라
                    // 글자마다 따로 그리면 글자와 단어 사이가 벌어져 보인다. 두 칸 이상 띄우면 다른 열로 본다.
                    StringBuilder run = new StringBuilder(glyph);
                    int next = col + 2;
                    while (next < current.columns()) {
                        int width = runWidthAt(current, next, row, style, font);
                        if (width == 0 && current.codePointAt(next, row) == ' ' && next + 1 < current.columns()
                                && style.equals(current.styleAt(next, row))
                                && runWidthAt(current, next + 1, row, style, font) > 0) {
                            width = 1;
                        }
                        if (width == 0) {
                            break;
                        }
                        run.appendCodePoint(current.codePointAt(next, row));
                        next += width;
                    }
                    glyph = run.toString();
                    col = next - 1;
                    // 문구 바로 뒤의 닫는 괄호는 칸 위치 대신 문구 끝에 붙여 그린다.
                    if (next < current.columns() && style.equals(current.styleAt(next, row))
                            && (current.codePointAt(next, row) == ']' || current.codePointAt(next, row) == ')')) {
                        closing = current.codePointAt(next, row);
                        col = next;
                    }
                }
                drawText(g, current, style, font, glyph, x, y);
                if (closing >= 0) {
                    Font bracketFont = fontFor(closing, style.bold());
                    drawText(g, current, style, bracketFont, String.valueOf((char) closing),
                            x + g.getFontMetrics(font).stringWidth(glyph) + 1, y);
                }
            }
        }
    }

    private void drawText(Graphics2D g, TextFrame current, TextStyle style, Font font, String text, int x, int y) {
        g.setFont(font);
        if (current.backdrop() != null && style.background().equals(current.styleAt(0, 0).background())) {
            // 그림 위에서도 읽히도록 글자 둘레에 어두운 테두리와 그림자를 두른다.
            g.setColor(TEXT_SHADOW);
            for (int[] offset : SHADOW_OFFSETS) {
                g.drawString(text, x + offset[0], y + baselines.get(font) + offset[1]);
            }
        }
        g.setColor(style.foreground());
        g.drawString(text, x, y + baselines.get(font));
    }

    /**
     * (col, row)의 글자를 앞 문구에 이어 그릴 수 있으면 그 글자가 차지하는 칸 수, 아니면 0. 같은 스타일이고
     * 문구의 글꼴로 그릴 수 있는 글자만 잇는다. 공백·테두리·타일·빈 칸 표시는 잇지 않는다.
     */
    private int runWidthAt(TextFrame current, int col, int row, TextStyle style, Font font) {
        if (current.isContinuation(col, row) || current.tileAt(col, row) != null
                || current.isTileContinuation(col, row) || !style.equals(current.styleAt(col, row))) {
            return 0;
        }
        int codePoint = current.codePointAt(col, row);
        if (codePoint == ' ' || codePoint == '・' || codePoint == '█' || (codePoint >= 0x2500 && codePoint <= 0x257F)
                || BRACKETS.indexOf(codePoint) >= 0 || !font.canDisplay(codePoint)) {
            return 0;
        }
        int width = TextFrame.isWide(codePoint) ? 2 : 1;
        return col + width <= current.columns() ? width : 0;
    }

    /**
     * 상자 테두리 글자(─ │ ┌ ┐ └ ┘)를 칸 가운데를 지나는 선으로 그린다. 모서리는 둥글게 꺾는다.
     * 글꼴마다 테두리 글자 크기가 달라 선이 끊겨 보이는 문제도 함께 사라진다.
     */
    private boolean paintLine(Graphics2D g, int codePoint, int x, int y) {
        if (codePoint < '─' || codePoint > '┘' || "─│┌┐└┘".indexOf(codePoint) < 0) {
            return false;
        }
        // 곧은 선은 픽셀 경계에 맞춘 사각형으로 그려 칸 이음매에 얼룩이 생기지 않게 한다.
        int w = Math.max(1, Math.round(cellHeight / 14f));
        int lineX = x + (cellWidth - w) / 2;
        int lineY = y + (cellHeight - w) / 2;
        int r = Math.min(cellWidth, cellHeight) / 2;
        float cx = lineX + w / 2f;
        float cy = lineY + w / 2f;
        Path2D.Float curve = new Path2D.Float();
        switch (codePoint) {
            case '─' -> g.fillRect(x, lineY, cellWidth, w);
            case '│' -> g.fillRect(lineX, y, w, cellHeight);
            case '┌' -> {
                g.fillRect(lineX + r, lineY, x + cellWidth - lineX - r, w);
                g.fillRect(lineX, lineY + r, w, y + cellHeight - lineY - r);
                curve.moveTo(cx + r, cy);
                curve.quadTo(cx, cy, cx, cy + r);
            }
            case '┐' -> {
                g.fillRect(x, lineY, lineX + w - r - x, w);
                g.fillRect(lineX, lineY + r, w, y + cellHeight - lineY - r);
                curve.moveTo(cx - r, cy);
                curve.quadTo(cx, cy, cx, cy + r);
            }
            case '└' -> {
                g.fillRect(lineX, y, w, lineY + w - r - y);
                g.fillRect(lineX + r, lineY, x + cellWidth - lineX - r, w);
                curve.moveTo(cx, cy - r);
                curve.quadTo(cx, cy, cx + r, cy);
            }
            default -> {
                g.fillRect(lineX, y, w, lineY + w - r - y);
                g.fillRect(x, lineY, lineX + w - r - x, w);
                curve.moveTo(cx, cy - r);
                curve.quadTo(cx, cy, cx - r, cy);
            }
        }
        if (codePoint != '─' && codePoint != '│') {
            g.setStroke(new BasicStroke(w));
            g.draw(curve);
        }
        return true;
    }
}
