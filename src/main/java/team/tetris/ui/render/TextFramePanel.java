package team.tetris.ui.render;

import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.font.TextLayout;
import java.util.Objects;
import javax.swing.JComponent;
import team.tetris.application.model.Settings.ScreenSize;

/**
 * {@link TextFrame}을 고정폭 글꼴로 그리는 Swing 컴포넌트. 화면 크기 설정은 글자 크기만 바꾸고,
 * 격자 크기(보드 10×20 포함)는 그대로다.
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
    /** 세로로 가장 긴 글자들. 이 글자들의 잉크가 칸 가운데에 오도록 기준선을 잡는다. */
    private static final String TALL_GLYPHS = "│█";

    private TextFrame frame;
    private Font plainFont;
    private Font boldFont;
    private int fontSize;
    private int cellWidth;
    private int cellHeight;
    private int baseline;
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
        Font font = new Font(Font.MONOSPACED, Font.PLAIN, size);
        return new Dimension(frame.columns() * getFontMetrics(font).charWidth('M'),
                frame.rows() * lineHeight(size));
    }

    private static int lineHeight(int size) {
        return Math.round(size * LINE_HEIGHT_RATIO);
    }

    private static boolean fits(Dimension size, Dimension limit) {
        return size.width <= limit.width && size.height <= limit.height;
    }

    private void applyFontSize(int size) {
        fontSize = size;
        plainFont = new Font(Font.MONOSPACED, Font.PLAIN, size);
        boldFont = plainFont.deriveFont(Font.BOLD);
        FontMetrics metrics = getFontMetrics(plainFont);
        cellWidth = metrics.charWidth('M');
        cellHeight = lineHeight(size);
        Rectangle ink = new TextLayout(TALL_GLYPHS, plainFont, metrics.getFontRenderContext())
                .getPixelBounds(null, 0, 0);
        // 잉크 위쪽은 기준선 위 -ink.y, 높이는 ink.height. 남는 공간을 위아래로 나눈다.
        baseline = Math.round((cellHeight - ink.height) / 2f) - ink.y;
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        Graphics2D g = (Graphics2D) graphics.create();
        try {
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            TextFrame current = frame;
            g.setColor(current.styleAt(0, 0).background());
            g.fillRect(0, 0, getWidth(), getHeight());
            for (int row = 0; row < current.rows(); row++) {
                for (int col = 0; col < current.columns(); col++) {
                    g.setColor(current.styleAt(col, row).background());
                    g.fillRect(col * cellWidth, row * cellHeight, cellWidth, cellHeight);
                }
            }
            for (int row = 0; row < current.rows(); row++) {
                for (int col = 0; col < current.columns(); col++) {
                    if (current.isContinuation(col, row)) {
                        continue;
                    }
                    int codePoint = current.codePointAt(col, row);
                    if (codePoint == ' ') {
                        continue;
                    }
                    TextStyle style = current.styleAt(col, row);
                    g.setFont(style.bold() ? boldFont : plainFont);
                    g.setColor(style.foreground());
                    String glyph = new String(Character.toChars(codePoint));
                    int x = col * cellWidth;
                    if (TextFrame.isWide(codePoint)) {
                        // 넓은 글자는 두 칸 가운데에 그려서 글자 사이가 고르게 보이게 한다.
                        x += Math.max(0, (2 * cellWidth - g.getFontMetrics().stringWidth(glyph)) / 2);
                    }
                    g.drawString(glyph, x, row * cellHeight + baseline);
                }
            }
        } finally {
            g.dispose();
        }
    }
}
