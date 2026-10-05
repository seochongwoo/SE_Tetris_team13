package team.tetris.ui.render.palette;

import java.awt.Color;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import team.tetris.core.TetrominoType;
import team.tetris.ui.render.TextStyle;

/**
 * 화면 색 구성. 기본 팔레트는 참조 코드의 블록 색을 바탕으로 검은 배경에서 잘 보이게 다듬었고,
 * 색맹 팔레트는 적록·청황 색각 이상 모두에서 구분되도록 고안된 Okabe-Ito 색 조합을 쓴다.
 * 어느 팔레트든 블록 무늬({@link BlockGlyphs})가 함께 그려지므로 색에만 의존하지 않는다.
 */
public record ColorPalette(
        boolean colorBlind,
        Map<TetrominoType, Color> blocks,
        Color background,
        Color text,
        Color dim,
        Color accent,
        Color warning,
        Color border,
        Color highlight) {

    public ColorPalette {
        blocks = Map.copyOf(new EnumMap<>(blocks));
        for (TetrominoType type : TetrominoType.values()) {
            Objects.requireNonNull(blocks.get(type), () -> "Missing color for " + type);
        }
        Objects.requireNonNull(background, "background");
        Objects.requireNonNull(text, "text");
        Objects.requireNonNull(dim, "dim");
        Objects.requireNonNull(accent, "accent");
        Objects.requireNonNull(warning, "warning");
        Objects.requireNonNull(border, "border");
        Objects.requireNonNull(highlight, "highlight");
    }

    public static ColorPalette of(boolean colorBlindMode) {
        return colorBlindMode ? colorBlindFriendly() : standard();
    }

    public static ColorPalette standard() {
        Map<TetrominoType, Color> blocks = new EnumMap<>(TetrominoType.class);
        blocks.put(TetrominoType.I, new Color(0, 255, 255));
        blocks.put(TetrominoType.O, new Color(255, 255, 0));
        blocks.put(TetrominoType.T, new Color(255, 0, 255));
        blocks.put(TetrominoType.S, new Color(0, 255, 0));
        blocks.put(TetrominoType.Z, new Color(255, 0, 0));
        blocks.put(TetrominoType.J, new Color(60, 110, 255));
        blocks.put(TetrominoType.L, new Color(255, 140, 0));
        return new ColorPalette(false, blocks, Color.BLACK, new Color(230, 230, 230), new Color(110, 110, 110),
                new Color(255, 215, 0), new Color(255, 90, 90), new Color(200, 200, 200), new Color(255, 215, 0));
    }

    public static ColorPalette colorBlindFriendly() {
        Map<TetrominoType, Color> blocks = new EnumMap<>(TetrominoType.class);
        blocks.put(TetrominoType.I, new Color(86, 180, 233));
        blocks.put(TetrominoType.O, new Color(240, 228, 66));
        blocks.put(TetrominoType.T, new Color(204, 121, 167));
        blocks.put(TetrominoType.S, new Color(0, 158, 115));
        blocks.put(TetrominoType.Z, new Color(213, 94, 0));
        blocks.put(TetrominoType.J, new Color(0, 114, 178));
        blocks.put(TetrominoType.L, new Color(230, 159, 0));
        return new ColorPalette(true, blocks, Color.BLACK, Color.WHITE, new Color(130, 130, 130),
                new Color(240, 228, 66), new Color(230, 159, 0), new Color(220, 220, 220), new Color(86, 180, 233));
    }

    public Color block(TetrominoType type) {
        return blocks.get(type);
    }

    public TextStyle base() {
        return new TextStyle(text, background, false);
    }

    public TextStyle dimStyle() {
        return new TextStyle(dim, background, false);
    }

    public TextStyle accentStyle() {
        return new TextStyle(accent, background, true);
    }

    public TextStyle warningStyle() {
        return new TextStyle(warning, background, true);
    }

    public TextStyle borderStyle() {
        return new TextStyle(border, background, false);
    }

    public TextStyle blockStyle(TetrominoType type) {
        return new TextStyle(block(type), background, true);
    }

    /** 선택된 항목처럼 강조할 줄: 배경을 칠하고 글자는 배경색으로 반전. */
    public TextStyle highlightStyle() {
        return new TextStyle(background, highlight, true);
    }
}
