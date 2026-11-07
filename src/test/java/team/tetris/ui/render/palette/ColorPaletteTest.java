package team.tetris.ui.render.palette;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Color;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;
import team.tetris.core.AttackBlock;
import team.tetris.core.TetrominoType;

class ColorPaletteTest {

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void everyBlockHasItsOwnColor(boolean colorBlind) {
        ColorPalette palette = ColorPalette.of(colorBlind);
        Set<Color> colors = new HashSet<>();
        for (TetrominoType type : TetrominoType.values()) {
            colors.add(palette.block(type));
        }

        assertEquals(TetrominoType.values().length, colors.size());
        assertEquals(colorBlind, palette.colorBlind());
    }

    @Test
    void everyBlockHasItsOwnPatternAndMirroredBlocksUseMirroredPatterns() {
        Set<String> glyphs = new HashSet<>();
        for (TetrominoType type : TetrominoType.values()) {
            assertEquals(2, BlockGlyphs.of(type).length());
            glyphs.add(BlockGlyphs.of(type));
        }

        assertEquals(TetrominoType.values().length, glyphs.size());
        assertEquals("((", BlockGlyphs.of(TetrominoType.J));
        assertEquals("))", BlockGlyphs.of(TetrominoType.L));
        assertFalse(glyphs.contains(BlockGlyphs.EMPTY));
    }

    @Test
    void stylesUseThePaletteColors() {
        ColorPalette palette = ColorPalette.standard();

        assertEquals(palette.block(TetrominoType.T), palette.blockStyle(TetrominoType.T).foreground());
        assertTrue(palette.blockStyle(TetrominoType.T).bold());
        assertSame(palette.background(), palette.base().background());
        assertEquals(palette.highlight(), palette.highlightStyle().background());
        assertEquals(palette.warning(), palette.warningStyle().foreground());
        assertEquals(palette.dim(), palette.dimStyle().foreground());
        assertEquals(palette.border(), palette.borderStyle().foreground());
        assertEquals(palette.accent(), palette.accentStyle().foreground());
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void attackCellsAreGreyAndDifferFromBlocksTextAndEmptyCells(boolean colorBlind) {
        // 흰색에 가까운 글자색이면 줄 삭제 점멸과 헷갈린다.
        ColorPalette palette = ColorPalette.of(colorBlind);
        Color attack = palette.blockTile(AttackBlock.INSTANCE).color();

        assertEquals(attack.getRed(), attack.getGreen());
        assertFalse(attack.equals(palette.text()) || attack.equals(palette.dim()));
        for (TetrominoType type : TetrominoType.values()) {
            assertFalse(attack.equals(palette.block(type)));
        }
        assertEquals(BlockGlyphs.ATTACK, BlockGlyphs.of(AttackBlock.INSTANCE));
    }
}
