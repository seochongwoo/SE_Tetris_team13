package team.tetris.ui.render.palette;

import java.awt.Color;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import team.tetris.core.Shape;
import team.tetris.core.TetrominoType;
import team.tetris.core.item.Item;
import team.tetris.core.item.WeightBlock;
import team.tetris.ui.render.BlockTile;
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

    /** 두 팔레트 공통 배경: 블록 색이 잘 보이도록 검정에 가까운 짙은 남색. */
    static final Color BACKGROUND = new Color(12, 14, 24);
    /** 두 팔레트 공통 선택 막대 색: 로고와 어울리는 따뜻한 노랑. 글자는 배경색으로 반전한다. */
    static final Color HIGHLIGHT = new Color(230, 185, 58);

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
        return new ColorPalette(false, blocks, BACKGROUND, new Color(230, 230, 230), new Color(110, 110, 110),
                new Color(255, 215, 0), new Color(255, 90, 90), new Color(200, 200, 200), HIGHLIGHT);
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
        return new ColorPalette(true, blocks, BACKGROUND, Color.WHITE, new Color(130, 130, 130),
                new Color(240, 228, 66), new Color(230, 159, 0), new Color(220, 220, 220), HIGHLIGHT);
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

    /** 두 팔레트 모두 삭제할 줄 전체를 흰색으로 강조한다. */
    public TextStyle lineClearStyle() {
        return new TextStyle(Color.BLACK, Color.WHITE, true);
    }

    /** 디버그 삭제 예고: 블록 글자색은 유지하고 배경에 흰색을 25% 섞어 강조한다. */
    public TextStyle removalPreviewStyle(Shape shape) {
        return blockStyle(shape).withBackground(new Color(
                (background.getRed() * 3 + 255) / 4,
                (background.getGreen() * 3 + 255) / 4,
                (background.getBlue() * 3 + 255) / 4));
    }

    /** 블록 칸의 스타일. 테트로미노가 아닌 블록(무게추 등)은 글자색으로 그린다. */
    public TextStyle blockStyle(Shape shape) {
        return new TextStyle(colorOf(shape), background, true);
    }

    /** 아이템이 실린 칸: 블록 색을 배경으로 칠하고 글자는 배경색으로 반전해 일반 무늬와 구분한다. */
    public TextStyle itemStyle(Shape shape) {
        return new TextStyle(background, colorOf(shape), true);
    }

    /** 보드 빈 칸 타일. */
    public BlockTile emptyTile() {
        return new BlockTile(BlockTile.Kind.EMPTY, dim, BlockTile.Pattern.NONE, null);
    }

    /** 블록 칸 타일: 블록 색과 블록별 무늬. */
    public BlockTile blockTile(Shape shape) {
        return new BlockTile(BlockTile.Kind.SOLID, colorOf(shape), BlockGlyphs.patternOf(shape), null);
    }

    /** 착지 그림자 타일: 같은 색·무늬의 테두리만 그린다. */
    public BlockTile ghostTile(Shape shape) {
        return new BlockTile(BlockTile.Kind.GHOST, colorOf(shape), BlockGlyphs.patternOf(shape), null);
    }

    /** 아이템이 실린 칸 타일: 무늬 대신 아이템 문자를 그린다. */
    public BlockTile itemTile(Shape shape, Item item) {
        return new BlockTile(BlockTile.Kind.ITEM, colorOf(shape), BlockTile.Pattern.NONE, String.valueOf(item.symbol()));
    }

    /** 디버그 삭제 예고 타일. item이 있으면 아이템 문자를 함께 그린다. */
    public BlockTile previewTile(Shape shape, Item item) {
        String label = item == null || item instanceof WeightBlock ? null : String.valueOf(item.symbol());
        return new BlockTile(BlockTile.Kind.PREVIEW, colorOf(shape),
                label == null ? BlockGlyphs.patternOf(shape) : BlockTile.Pattern.NONE, label);
    }

    private Color colorOf(Shape shape) {
        return shape instanceof TetrominoType type ? block(type) : text;
    }

    /** 선택된 항목처럼 강조할 줄: 배경을 칠하고 글자는 배경색으로 반전. */
    public TextStyle highlightStyle() {
        return new TextStyle(background, highlight, true);
    }
}
