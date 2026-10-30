package team.tetris.ui.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import team.tetris.application.model.Settings.ScreenSize;
import team.tetris.core.TetrominoType;
import team.tetris.ui.render.palette.BlockGlyphs;
import team.tetris.ui.render.palette.ColorPalette;

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

    private static BufferedImage paint(TextFrame frame) {
        var panel = new TextFramePanel(ScreenSize.MEDIUM, frame);
        panel.setSize(panel.getPreferredSize());
        var image = new BufferedImage(panel.getWidth(), panel.getHeight(), BufferedImage.TYPE_INT_RGB);
        var graphics = image.createGraphics();
        try {
            panel.paint(graphics);
        } finally {
            graphics.dispose();
        }
        return image;
    }

    private static String pixels(BufferedImage image) {
        StringBuilder text = new StringBuilder();
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                text.append(Integer.toHexString(image.getRGB(x, y))).append(',');
            }
        }
        return text.toString();
    }

    @Test
    void everyBlockPatternLooksDifferentEvenInTheSameColor() {
        // 색을 구분하지 못해도 무늬만으로 블록을 알아볼 수 있어야 한다.
        var style = new TextStyle(Color.WHITE, Color.BLACK, false);
        Set<String> seen = new HashSet<>();
        for (BlockTile.Pattern pattern : BlockTile.Pattern.values()) {
            var frame = new TextFrame(2, 1, style);
            frame.putTile(0, 0, "  ", style, new BlockTile(BlockTile.Kind.SOLID, Color.GRAY, pattern, null));
            assertTrue(seen.add(pixels(paint(frame))), "Pattern looks like another: " + pattern);
        }
    }

    @Test
    void tetrominoesUseSevenDifferentPatterns() {
        Set<BlockTile.Pattern> patterns = new HashSet<>();
        for (TetrominoType type : TetrominoType.values()) {
            patterns.add(ColorPalette.colorBlindFriendly().blockTile(type).pattern());
        }
        assertEquals(TetrominoType.values().length, patterns.size());
    }

    @Test
    void tilesReplaceTheirGlyphOnScreen() {
        var style = new TextStyle(Color.WHITE, Color.BLACK, false);
        var withTile = new TextFrame(2, 1, style);
        withTile.putTile(0, 0, "##", style, new BlockTile(BlockTile.Kind.SOLID, Color.RED, BlockTile.Pattern.NONE, null));
        var textOnly = new TextFrame(2, 1, style);
        textOnly.put(0, 0, "##", style);

        BufferedImage tile = paint(withTile);
        // 타일 가운데는 블록 색(빨강 계열)으로 칠해진다.
        Color center = new Color(tile.getRGB(tile.getWidth() / 2, tile.getHeight() / 2));
        assertTrue(center.getRed() > 150 && center.getGreen() < 80, "center: " + center);
        assertNotEquals(pixels(paint(textOnly)), pixels(tile));
    }

    @Test
    void shadedCellsAreDarkerThanTheSameCellsUnshaded() {
        ColorPalette palette = ColorPalette.standard();
        var frame = new TextFrame(4, 1, palette.base());
        frame.putTile(0, 0, "[]", palette.blockStyle(TetrominoType.I), palette.blockTile(TetrominoType.I));
        Color bright = new Color(paint(frame).getRGB(5, 10));

        frame.shade();
        Color dark = new Color(paint(frame).getRGB(5, 10));

        assertTrue(dark.getBlue() < bright.getBlue(), bright + " -> " + dark);
    }

    @Test
    void paintsEveryKindOfCellWithoutFailing() {
        ColorPalette palette = ColorPalette.standard();
        var frame = new TextFrame(30, 4, palette.base());
        frame.box(0, 0, 30, 4, palette.borderStyle(), palette.base());
        frame.put(1, 1, "► 게임 시작   ", palette.highlightStyle());
        frame.put(16, 1, "사용할 수 있는 키: ← →", palette.warningStyle());
        frame.putTile(1, 2, BlockGlyphs.EMPTY, palette.dimStyle(), palette.emptyTile());
        frame.putTile(3, 2, BlockGlyphs.GHOST, palette.blockStyle(TetrominoType.T), palette.ghostTile(TetrominoType.T));
        frame.putTile(5, 2, "L ", palette.itemStyle(TetrominoType.S),
                new BlockTile(BlockTile.Kind.ITEM, palette.block(TetrominoType.S), BlockTile.Pattern.NONE, "L"));
        frame.putTile(7, 2, "##", palette.removalPreviewStyle(TetrominoType.O), palette.previewTile(TetrominoType.O, null));
        frame.putTile(9, 2, "B ", palette.removalPreviewStyle(TetrominoType.Z),
                new BlockTile(BlockTile.Kind.PREVIEW, palette.block(TetrominoType.Z), BlockTile.Pattern.NONE, "B"));
        frame.put(11, 2, "█・", palette.blockStyle(TetrominoType.L));

        BufferedImage image = paint(frame);

        assertTrue(image.getWidth() > 0 && image.getHeight() > 0);
    }

    @Test
    void symbolAfterKoreanTextIsDrawnWithAFontThatHasIt() {
        // 설정 화면의 "◄ 보통 ►": 맑은 고딕에는 ►가 없어 한글 문구에 이어 그리면 깨져 보였다.
        var style = new TextStyle(Color.WHITE, Color.BLACK, false);
        var phrase = new TextFrame(8, 1, style);
        phrase.put(0, 0, "보통 ►", style);
        var alone = new TextFrame(8, 1, style);
        alone.put(5, 0, "►", style);

        BufferedImage withText = paint(phrase);
        BufferedImage symbolOnly = paint(alone);
        int cellWidth = withText.getWidth() / 8;
        for (int y = 0; y < withText.getHeight(); y++) {
            for (int x = 5 * cellWidth; x < 6 * cellWidth; x++) {
                assertEquals(symbolOnly.getRGB(x, y), withText.getRGB(x, y), "pixel " + x + "," + y);
            }
        }
    }

    /** y행부터 끝까지의 픽셀. */
    private static String pixelsFrom(BufferedImage image, int fromY, int toY) {
        return pixels(image.getSubimage(0, fromY, image.getWidth(), toY - fromY));
    }

    @Test
    void castleStaysStillWhileCloudsDriftBy() {
        ColorPalette palette = ColorPalette.standard();
        var early = TextFrame.blank(palette.base());
        early.setBackdrop(Backdrop.castle(0));
        var later = TextFrame.blank(palette.base());
        later.setBackdrop(Backdrop.castle(3_000_000_000L));

        BufferedImage first = paint(early);
        BufferedImage second = paint(later);
        int height = first.getHeight();

        // 아래쪽 성은 그대로이고, 위쪽 하늘의 구름만 움직인다.
        assertEquals(pixelsFrom(first, height * 3 / 4, height), pixelsFrom(second, height * 3 / 4, height));
        assertNotEquals(pixelsFrom(first, 0, height / 2), pixelsFrom(second, 0, height / 2));
        // 같은 시점은 언제나 같은 그림이다.
        assertEquals(pixels(first), pixels(paint(early)));
    }

    @Test
    void driftBackdropLeavesTheHoleUntouchedAndMovesElsewhere() {
        ColorPalette palette = ColorPalette.standard();
        Rectangle hole = new Rectangle(0, 0, 22, TextFrame.ROWS);
        BufferedImage plain = paint(TextFrame.blank(palette.base()));
        var early = TextFrame.blank(palette.base());
        early.setBackdrop(Backdrop.drift(0, 0, hole));
        var later = TextFrame.blank(palette.base());
        later.setBackdrop(Backdrop.drift(4_000_000_000L, 3, hole));

        BufferedImage first = paint(early);
        BufferedImage second = paint(later);
        int holeWidth = first.getWidth() * 22 / TextFrame.COLUMNS;

        assertEquals(pixels(plain.getSubimage(0, 0, holeWidth, plain.getHeight())),
                pixels(first.getSubimage(0, 0, holeWidth, first.getHeight())));
        assertNotEquals(pixels(first), pixels(second));
    }
}
