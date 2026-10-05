package team.tetris.ui.render;

import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.util.Objects;
import javax.swing.JComponent;
import team.tetris.application.model.Settings.ScreenSize;

/**
 * {@link TextFrame}을 고정폭 글꼴로 그리는 Swing 컴포넌트. 화면 크기 설정은 글자 크기만 바꾸고,
 * 격자 크기(보드 10×20 포함)는 그대로다.
 *
 * <p>각 글자를 자기 칸의 절대 위치에 찍기 때문에, 글꼴에서 한글 폭이 정확히 두 칸이 아니어도
 * 오차가 옆 칸으로 누적되지 않는다.
 */
public final class TextFramePanel extends JComponent {

    private TextFrame frame;
    private Font plainFont;
    private Font boldFont;
    private int cellWidth;
    private int cellHeight;
    private int ascent;

    public TextFramePanel(ScreenSize size, TextFrame initial) {
        this.frame = Objects.requireNonNull(initial, "initial");
        setFocusable(true);
        setFocusTraversalKeysEnabled(false);
        setOpaque(true);
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
        plainFont = new Font(Font.MONOSPACED, Font.PLAIN, fontSizeFor(size));
        boldFont = plainFont.deriveFont(Font.BOLD);
        FontMetrics metrics = getFontMetrics(plainFont);
        cellWidth = metrics.charWidth('M');
        cellHeight = metrics.getHeight();
        ascent = metrics.getAscent();
        revalidate();
        repaint();
    }

    public void show(TextFrame next) {
        frame = Objects.requireNonNull(next, "next");
        repaint();
    }

    @Override
    public Dimension getPreferredSize() {
        return new Dimension(frame.columns() * cellWidth, frame.rows() * cellHeight);
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
                    g.drawString(glyph, x, row * cellHeight + ascent);
                }
            }
        } finally {
            g.dispose();
        }
    }
}
