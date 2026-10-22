package team.tetris.ui.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.image.BufferedImage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import team.tetris.application.model.Settings.ScreenSize;
import team.tetris.ui.render.palette.BlockGlyphs;

class TextFramePanelTest {

    private static TextFramePanel panel(ScreenSize size) {
        return new TextFramePanel(size, TextFrame.blank(new TextStyle(Color.WHITE, Color.BLACK, false)));
    }

    @ParameterizedTest
    @CsvSource({"SMALL,1", "MEDIUM,1", "LARGE,1", "SMALL,2", "MEDIUM,2", "LARGE,2"})
    void emptyBoardMarkerIsCenteredAcrossTheWholeBlockCellAtEveryScreenSize(ScreenSize size, int scale) {
        var style = new TextStyle(Color.WHITE, Color.BLACK, false);
        assertEquals(2, TextFrame.displayWidth(BlockGlyphs.EMPTY));
        var frame = new TextFrame(2, 1, style);
        frame.put(0, 0, BlockGlyphs.EMPTY, style);
        var panel = new TextFramePanel(size, frame);
        panel.setSize(panel.getPreferredSize());
        var image = new BufferedImage(panel.getWidth() * scale, panel.getHeight() * scale, BufferedImage.TYPE_INT_RGB);
        var graphics = image.createGraphics();
        try {
            graphics.scale(scale, scale);
            panel.paint(graphics);
        } finally {
            graphics.dispose();
        }
        int minX = image.getWidth(), minY = image.getHeight(), maxX = -1, maxY = -1;
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                if (image.getRGB(x, y) != Color.BLACK.getRGB()) {
                    minX = Math.min(minX, x);
                    minY = Math.min(minY, y);
                    maxX = Math.max(maxX, x);
                    maxY = Math.max(maxY, y);
                }
            }
        }
        assertTrue(maxX >= minX && maxY >= minY, "Marker must be visible: " + size);
        assertEquals(image.getWidth() / 2.0, (minX + maxX + 1) / 2.0, 1.0, "Horizontal: " + size);
        assertEquals(image.getHeight() / 2.0, (minY + maxY + 1) / 2.0, 1.0, "Vertical: " + size);
    }

    @Test
    void eachPresetUsesItsOwnFontSizeAndGetsLarger() {
        TextFramePanel small = panel(ScreenSize.SMALL);
        TextFramePanel medium = panel(ScreenSize.MEDIUM);
        TextFramePanel large = panel(ScreenSize.LARGE);

        assertEquals(14, small.fontSize());
        assertEquals(18, medium.fontSize());
        assertEquals(24, large.fontSize());
        assertTrue(small.getPreferredSize().height < medium.getPreferredSize().height);
        assertTrue(medium.getPreferredSize().height < large.getPreferredSize().height);
    }

    @Test
    void rowsAreOnlySlightlyTallerThanTheFont() {
        TextFramePanel medium = panel(ScreenSize.MEDIUM);

        // 18px × 1.1 → 20px × 24줄. 1080p·배율 150% 노트북(사용 가능 높이 약 640px)에도 들어간다.
        assertEquals(20 * TextFrame.ROWS, medium.getPreferredSize().height);
    }

    @Test
    void shrinksTheFontUntilThePanelFitsTheScreen() {
        TextFramePanel large = panel(ScreenSize.LARGE);
        Dimension limit = new Dimension(10_000, 500);

        large.setScreenSize(ScreenSize.LARGE, limit);

        assertTrue(large.fontSize() < 24);
        assertTrue(large.getPreferredSize().height <= limit.height);
    }

    @Test
    void keepsThePresetWhenItFitsAndNeverGoesBelowTheMinimum() {
        TextFramePanel panel = panel(ScreenSize.LARGE);

        panel.setScreenSize(ScreenSize.LARGE, new Dimension(10_000, 10_000));
        assertEquals(24, panel.fontSize());

        panel.setScreenSize(ScreenSize.LARGE, new Dimension(1, 1));
        assertEquals(TextFramePanel.MIN_FONT_SIZE, panel.fontSize());
    }

    @Test
    void inputMethodIsOffUntilTextInputIsRequested() {
        TextFramePanel panel = panel(ScreenSize.MEDIUM);
        assertFalse(panel.isTextInput());

        panel.setTextInput(true);
        assertTrue(panel.isTextInput());

        panel.setTextInput(false);
        assertFalse(panel.isTextInput());
    }
}
