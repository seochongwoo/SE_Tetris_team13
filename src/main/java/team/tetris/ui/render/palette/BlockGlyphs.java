package team.tetris.ui.render.palette;

import team.tetris.core.AttackBlock;
import team.tetris.core.Shape;
import team.tetris.core.TemporaryBlock;
import team.tetris.core.TetrominoType;
import team.tetris.core.item.Item;
import team.tetris.core.item.WeightBlock;
import team.tetris.ui.render.BlockTile.Pattern;

/**
 * 블록 한 칸을 그리는 두 글자 무늬. 색을 구분하기 어려워도 모양과 무늬만으로 블록을 알아볼 수
 * 있도록 블록마다 다르게 두고, 좌우가 뒤집힌 블록(S/Z, J/L)은 서로 거울상인 무늬를 쓴다.
 */
public final class BlockGlyphs {

    /** 빈 칸: 블록 한 칸과 같은 두 글자 폭의 전각 가운뎃점. */
    public static final String EMPTY = "・";

    /** 디버그 모드의 하드 드롭 착지 그림자. */
    public static final String GHOST = "░░";

    /** 테트로미노가 아닌 블록(무게추 등)의 무늬. */
    public static final String SPECIAL = "@@";

    /** 디버그 보드 채우기용 임시 블록. */
    public static final String TEMPORARY = "▒▒";

    /** 대전에서 상대의 공격으로 들어온 줄의 칸. */
    public static final String ATTACK = "▓▓";

    private BlockGlyphs() {
    }

    public static String of(Shape shape) {
        if (shape == TemporaryBlock.INSTANCE) return TEMPORARY;
        if (shape == AttackBlock.INSTANCE) return ATTACK;
        if (!(shape instanceof TetrominoType type)) {
            return SPECIAL;
        }
        return switch (type) {
            case I -> "[]";
            case O -> "##";
            case T -> "<>";
            case S -> "//";
            case Z -> "\\\\";
            case J -> "((";
            case L -> "))";
        };
    }

    /** 타일로 그릴 때의 무늬. 글자 무늬({@link #of(Shape)})와 같은 뜻을 그림으로 옮긴 것이다. */
    public static Pattern patternOf(Shape shape) {
        if (shape == TemporaryBlock.INSTANCE) return Pattern.CHECKER;
        // 공격 칸은 회색과 전용 가로줄 무늬를 함께 사용한다.
        if (shape == AttackBlock.INSTANCE) return Pattern.BARS;
        if (!(shape instanceof TetrominoType type)) {
            return Pattern.CROSS;
        }
        return switch (type) {
            case I -> Pattern.SQUARE;
            case O -> Pattern.DOT;
            case T -> Pattern.DIAMOND;
            case S -> Pattern.SLASH;
            case Z -> Pattern.BACKSLASH;
            case J -> Pattern.LEFT;
            case L -> Pattern.RIGHT;
        };
    }

    /** 아이템이 실린 칸: 아이템 문자 한 글자와 공백. {@code ColorPalette.itemStyle}과 함께 쓴다. */
    public static String of(Item item) {
        if (item instanceof WeightBlock) return SPECIAL;
        return item.symbol() + " ";
    }
}
