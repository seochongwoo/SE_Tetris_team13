package team.tetris.ui.render;

import java.awt.Color;
import java.util.Objects;

/**
 * 블록 한 칸(글자 두 칸)을 글자 대신 타일 그림으로 그릴 때 쓰는 정보. {@link TextFrame}에는 같은 칸에
 * 무늬 글자도 함께 찍히므로, 화면 로직과 테스트는 글자 격자만 보고 {@link TextFramePanel}만 타일을 그린다.
 *
 * <p>무늬({@link Pattern})는 블록 종류마다 다르게 두어 색을 구분하기 어려워도 블록을 알아볼 수 있게 한다.
 *
 * @param label 아이템 칸에 겹쳐 그릴 아이템 문자. 아이템이 아니면 null
 */
public record BlockTile(Kind kind, Color color, Pattern pattern, String label) {

    public enum Kind {
        /** 보드의 빈 칸. */
        EMPTY,
        /** 놓인 블록과 낙하 중인 블록. */
        SOLID,
        /** 하드 드롭 착지 그림자. */
        GHOST,
        /** 아이템이 실린 칸. 무늬 대신 아이템 문자를 그린다. */
        ITEM,
        /** 디버그 삭제 예고. 블록을 밝게 덮는다. */
        PREVIEW
    }

    /** 블록 안쪽 무늬. 좌우가 뒤집힌 블록(S/Z, J/L)은 서로 거울상인 무늬를 쓴다. */
    public enum Pattern {
        NONE, SQUARE, DOT, DIAMOND, SLASH, BACKSLASH, LEFT, RIGHT, CROSS, CHECKER
    }

    public BlockTile {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(color, "color");
        Objects.requireNonNull(pattern, "pattern");
    }
}
