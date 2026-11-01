package team.tetris.ui.render;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.Shape;
import java.awt.font.TextLayout;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;

/**
 * 블록 타일({@link BlockTile})을 그린다. 블록은 위가 밝고 아래가 어두운 둥근 사각형에 블록별 무늬를
 * 새기고, 아이템 칸은 무늬 대신 원 안에 아이템 문자를 그린다.
 */
final class TilePainter {

    /** 무늬 색: 어떤 블록 색 위에서도 보이도록 반투명 검정을 쓴다. */
    private static final Color INK = new Color(0, 0, 0, 125);
    private static final Color GLOSS = new Color(255, 255, 255, 70);
    private static final Color BADGE = new Color(15, 15, 25, 215);

    private TilePainter() {
    }

    static void paint(Graphics2D g, BlockTile tile, float x, float y, float width, float height,
            Color background, Font labelFont) {
        float gap = Math.max(1f, height / 14f);
        float arc = height / 3f;
        RoundRectangle2D.Float body = new RoundRectangle2D.Float(x + gap, y + gap,
                width - 2 * gap, height - 2 * gap, arc, arc);
        Color color = tile.color();
        switch (tile.kind()) {
            case EMPTY -> {
                g.setColor(mix(background, Color.WHITE, 0.05f));
                g.fill(body);
            }
            case SOLID -> {
                paintBody(g, body, color);
                paintPattern(g, tile.pattern(), body, INK);
            }
            case GHOST -> {
                g.setColor(withAlpha(color, 40));
                g.fill(body);
                g.setColor(withAlpha(color, 200));
                g.setStroke(new BasicStroke(Math.max(1f, height / 12f)));
                g.draw(body);
                paintPattern(g, tile.pattern(), body, withAlpha(color, 130));
            }
            case ITEM -> {
                paintBody(g, body, color);
                paintLabel(g, tile.label(), body, labelFont);
            }
            case PREVIEW -> {
                paintBody(g, body, color);
                g.setColor(new Color(255, 255, 255, 110));
                g.fill(body);
                if (tile.label() != null) {
                    paintLabel(g, tile.label(), body, labelFont);
                } else {
                    paintPattern(g, tile.pattern(), body, INK);
                }
            }
        }
    }

    /** 메뉴 제목처럼 글자 한 칸을 작은 블록으로 그린다. */
    static void paintPixel(Graphics2D g, Color color, float x, float y, float width, float height) {
        float gap = Math.max(0.5f, height / 24f);
        float arc = width / 3f;
        paintBody(g, new RoundRectangle2D.Float(x + gap, y + gap, width - 2 * gap, height - 2 * gap, arc, arc), color);
    }

    private static void paintBody(Graphics2D g, RoundRectangle2D.Float body, Color color) {
        g.setPaint(new GradientPaint(0, body.y, mix(color, Color.WHITE, 0.22f),
                0, body.y + body.height, mix(color, Color.BLACK, 0.28f)));
        g.fill(body);
        // 위쪽 절반에 옅은 광택을 넣는다.
        Shape clip = g.getClip();
        g.clip(body);
        g.setColor(GLOSS);
        g.fill(new Rectangle2D.Float(body.x, body.y, body.width, body.height * 0.18f));
        g.setClip(clip);
        g.setColor(mix(color, Color.BLACK, 0.45f));
        g.setStroke(new BasicStroke(1f));
        g.draw(body);
    }

    private static void paintPattern(Graphics2D g, BlockTile.Pattern pattern, RoundRectangle2D.Float body, Color ink) {
        float cx = (float) body.getCenterX();
        float cy = (float) body.getCenterY();
        float d = Math.min(body.width, body.height) * 0.5f;
        float h = d / 2;
        g.setColor(ink);
        g.setStroke(new BasicStroke(Math.max(1.2f, body.height / 9f), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        switch (pattern) {
            case NONE -> {
            }
            case SQUARE -> g.draw(new Rectangle2D.Float(cx - h, cy - h, d, d));
            case DOT -> {
                float r = d * 0.36f;
                g.fill(new Ellipse2D.Float(cx - r, cy - r, 2 * r, 2 * r));
            }
            case DIAMOND -> g.draw(polygon(cx, cy - h, cx + h, cy, cx, cy + h, cx - h, cy));
            case SLASH -> {
                g.draw(new Line2D.Float(cx - h, cy + h * 0.2f, cx - h * 0.2f, cy - h));
                g.draw(new Line2D.Float(cx + h * 0.2f, cy + h, cx + h, cy - h * 0.2f));
            }
            case BACKSLASH -> {
                g.draw(new Line2D.Float(cx + h, cy + h * 0.2f, cx + h * 0.2f, cy - h));
                g.draw(new Line2D.Float(cx - h * 0.2f, cy + h, cx - h, cy - h * 0.2f));
            }
            case LEFT -> g.fill(polygon(cx - h, cy, cx + h * 0.7f, cy - h, cx + h * 0.7f, cy + h));
            case RIGHT -> g.fill(polygon(cx + h, cy, cx - h * 0.7f, cy - h, cx - h * 0.7f, cy + h));
            case CROSS -> {
                g.draw(new Line2D.Float(cx - h, cy - h, cx + h, cy + h));
                g.draw(new Line2D.Float(cx - h, cy + h, cx + h, cy - h));
            }
            case CHECKER -> {
                g.fill(new Rectangle2D.Float(cx - h, cy - h, h, h));
                g.fill(new Rectangle2D.Float(cx, cy, h, h));
            }
        }
    }

    /** 어두운 원 안에 아이템 문자를 흰색으로 그린다. */
    private static void paintLabel(Graphics2D g, String label, RoundRectangle2D.Float body, Font font) {
        float cx = (float) body.getCenterX();
        float cy = (float) body.getCenterY();
        float r = Math.min(body.width, body.height) * 0.42f;
        g.setColor(BADGE);
        g.fill(new Ellipse2D.Float(cx - r, cy - r, 2 * r, 2 * r));
        Font sized = font.deriveFont(Font.BOLD, r * 1.45f);
        TextLayout layout = new TextLayout(label, sized, g.getFontRenderContext());
        Rectangle2D ink = layout.getBounds();
        g.setColor(Color.WHITE);
        layout.draw(g, (float) (cx - ink.getWidth() / 2 - ink.getX()), (float) (cy - ink.getHeight() / 2 - ink.getY()));
    }

    private static Path2D.Float polygon(float... points) {
        Path2D.Float path = new Path2D.Float();
        path.moveTo(points[0], points[1]);
        for (int i = 2; i < points.length; i += 2) {
            path.lineTo(points[i], points[i + 1]);
        }
        path.closePath();
        return path;
    }

    static Color mix(Color from, Color to, float amount) {
        return new Color(
                Math.round(from.getRed() + (to.getRed() - from.getRed()) * amount),
                Math.round(from.getGreen() + (to.getGreen() - from.getGreen()) * amount),
                Math.round(from.getBlue() + (to.getBlue() - from.getBlue()) * amount));
    }

    private static Color withAlpha(Color color, int alpha) {
        return new Color(color.getRed(), color.getGreen(), color.getBlue(), alpha);
    }
}
