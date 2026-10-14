package team.tetris.core.item;

import team.tetris.core.Position;

/**
 * 점수 2배 아이템 {@code $}. 일반 블록의 한 칸에 붙는다.
 *
 * <p>보드에는 아무 효과가 없다. 점수는 core가 다루지 않으므로, 엔진이 발동 사실을
 * {@code EngineStep#itemActivations()}로 보고하면 application의 세션이 이 아이템을 알아보고 이후 줄
 * 삭제 점수를 2배로 계산한다. 몇 번 동안 적용할지도 세션이 정한다.
 */
public final class DoubleScoreItem implements Item {

    public static final char SYMBOL = '$';

    @Override
    public char symbol() {
        return SYMBOL;
    }

    @Override
    public void onLock(ItemContext context, Position position) {
        // 보드 효과 없음. 점수 배율은 application이 처리한다.
    }
}
