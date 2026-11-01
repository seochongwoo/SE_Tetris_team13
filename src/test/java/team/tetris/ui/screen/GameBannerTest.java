package team.tetris.ui.screen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;
import team.tetris.core.TetrominoType;
import team.tetris.ui.render.TextFrame;
import team.tetris.ui.render.palette.ColorPalette;

class GameBannerTest {

    private final ColorPalette palette = ColorPalette.standard();

    @Test
    void fourLinesAtOnceShowTetris() {
        GameBanner banner = new GameBanner(0, 0);

        banner.observe(4, 0);

        assertEquals(List.of(GameBanner.TETRIS), banner.texts());
    }

    @Test
    void fewerLinesOrLinesSpreadOverSeveralClearsDoNotShowTetris() {
        GameBanner banner = new GameBanner(0, 0);

        banner.observe(3, 0);
        banner.observe(5, 0);
        banner.observe(5, 0);

        assertEquals(List.of(), banner.texts());
    }

    @Test
    void levelUpShowsTheNewLevel() {
        GameBanner banner = new GameBanner(9, 0);

        banner.observe(10, 1);

        assertEquals(List.of("LEVEL UP!  Lv 1"), banner.texts());
    }

    @Test
    void tetrisThatAlsoRaisesTheLevelShowsBoth() {
        GameBanner banner = new GameBanner(8, 0);

        banner.observe(12, 1);

        assertEquals(List.of(GameBanner.TETRIS, "LEVEL UP!  Lv 1"), banner.texts());
    }

    @Test
    void bannerBlinksBeforeItDisappears() {
        GameBanner banner = new GameBanner(0, 0);
        banner.observe(4, 0);

        // 마지막 0.4초 동안 0.1초 간격으로 보였다 사라진다.
        banner.advance(GameBanner.DURATION_NANOS - 250_000_000L);
        assertEquals(List.of(), banner.texts());
        banner.advance(100_000_000L);
        assertEquals(List.of(GameBanner.TETRIS), banner.texts());
        banner.advance(100_000_000L);
        assertEquals(List.of(), banner.texts());
        banner.advance(50_000_000L);
        banner.observe(4, 0);
        assertEquals(List.of(), banner.texts());
    }

    @Test
    void aNewTetrisRestartsTheSameBannerInsteadOfStacking() {
        GameBanner banner = new GameBanner(0, 0);
        banner.observe(4, 0);
        banner.advance(500_000_000L);

        banner.observe(8, 0);

        assertEquals(List.of(GameBanner.TETRIS), banner.texts());
    }

    @Test
    void drawsTetrisInBlockColorsOverTheBoard() {
        GameBanner banner = new GameBanner(0, 0);
        banner.observe(4, 0);
        TextFrame frame = TextFrame.blank(palette.base());

        banner.draw(frame, palette);
        // 처음에는 두 줄 아래에서 시작해 떠오른다.
        int row = GameBanner.TOP_ROW + 2;
        String text = frame.rowText(row);
        int col = text.indexOf('T');

        assertTrue(text.contains(GameBanner.TETRIS), text);
        assertEquals(palette.block(TetrominoType.I), frame.styleAt(col, row).foreground());
        assertEquals(palette.block(TetrominoType.O), frame.styleAt(col + 2, row).foreground());

        banner.advance(GameBanner.DURATION_NANOS / 2);
        TextFrame later = TextFrame.blank(palette.base());
        banner.draw(later, palette);
        assertTrue(later.rowText(GameBanner.TOP_ROW).contains(GameBanner.TETRIS));
    }

    @Test
    void drawsLevelUpBelowTetris() {
        GameBanner banner = new GameBanner(8, 0);
        banner.observe(12, 1);
        banner.advance(GameBanner.DURATION_NANOS / 2);
        TextFrame frame = TextFrame.blank(palette.base());

        banner.draw(frame, palette);

        String text = frame.rowText(GameBanner.TOP_ROW + 2);
        assertTrue(text.contains("LEVEL UP!  Lv 1"), text);
        assertEquals(palette.accent(), frame.styleAt(text.indexOf("LEVEL"), GameBanner.TOP_ROW + 2).foreground());
    }
}
