package team.tetris.core.rule;

import team.tetris.core.Piece;

/**
 * 엔진에 다음 블록(모양 + 아이템)을 공급한다. {@link PieceGenerator}가 "어떤 모양이 나올지"만
 * 정한다면, 이 인터페이스는 그 위에 "아이템을 실을지"까지 정한다.
 *
 * <p>일반 모드는 {@link PlainPieceSource}로 생성기를 감싸 쓴다. 아이템 모드처럼 지운 줄 수에 따라
 * 아이템을 내야 하는 공급자는 {@link #onLinesCleared}로 줄 수를 받는다.
 *
 * <p>peek() 직후에 호출한 next()는 방금 peek한 것과 같은 블록을 반환해야 한다.
 */
public interface PieceSource {

    /** 다음 블록을 꺼내고 내부 상태를 한 칸 전진시킨다. */
    Piece next();

    /** 다음에 next()가 반환할 블록을 상태 변화 없이 미리 본다 (미리보기 표시용). */
    Piece peek();

    /**
     * 블록이 고정되어 줄이 lines개 지워진 직후, 다음 블록을 꺼내기 전에 호출된다 (0이면 호출되지
     * 않는다). 이 시점에 미리보기 블록을 정하면, 지워진 결과가 곧바로 미리보기에 반영된다.
     */
    default void onLinesCleared(int lines) {
    }
}
