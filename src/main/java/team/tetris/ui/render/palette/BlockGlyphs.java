package team.tetris.ui.render.palette;

import team.tetris.core.TetrominoType;

/**
 * 블록 한 칸을 그리는 두 글자 무늬. 색을 구분하기 어려워도 모양과 무늬만으로 블록을 알아볼 수
 * 있도록 블록마다 다르게 두고, 좌우가 뒤집힌 블록(S/Z, J/L)은 서로 거울상인 무늬를 쓴다.
 */
public final class BlockGlyphs {

    /** 빈 칸. */
    public static final String EMPTY = " .";

    private BlockGlyphs() {
    }

    public static String of(TetrominoType type) {
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
}
