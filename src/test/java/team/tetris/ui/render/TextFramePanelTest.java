package team.tetris.ui.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Color;
import java.awt.Dimension;
import org.junit.jupiter.api.Test;
import team.tetris.application.model.Settings.ScreenSize;

class TextFramePanelTest {

    private static TextFramePanel panel(ScreenSize size) {
        return new TextFramePanel(size, TextFrame.blank(new TextStyle(Color.WHITE, Color.BLACK, false)));
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
