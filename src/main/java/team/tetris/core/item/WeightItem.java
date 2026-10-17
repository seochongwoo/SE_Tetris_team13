package team.tetris.core.item;

import team.tetris.core.Position;

/**
 * 무게추 아이템 {@code W}. 너비 4칸 전용 블록({@link WeightBlock})의 모든 칸에 실린다.
 *
 * <p>효과는 떨어지는 동안 엔진이 처리한다({@link DrillingItem}). 바닥에 고정된 뒤에는 아무 효과
 * 없이 일반 칸으로 보드에 남는다.
 */
public final class WeightItem implements DrillingItem {

    public static final char SYMBOL = 'W';

    @Override
    public char symbol() {
        return SYMBOL;
    }

    @Override
    public void onLock(ItemContext context, Position position) {
        // 효과는 낙하 중에 끝났다. 고정된 무게추는 일반 칸으로 남는다.
    }
}
