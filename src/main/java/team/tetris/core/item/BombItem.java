package team.tetris.core.item;

import team.tetris.core.Position;

/**
 * 폭탄 아이템 {@code B}. 1칸짜리 단독 블록({@link SoloBlock})으로 등장한다.
 *
 * <p>블록이 고정되면 자기 칸을 중심으로 3×3 범위의 칸을 모두 지운다(자기 칸 포함). 보드 밖으로
 * 나가는 범위는 무시한다. 지운 칸 위의 칸은 내려오지 않는다.
 */
public final class BombItem implements Item {

    public static final char SYMBOL = 'B';

    /** 중심에서 상하좌우로 지우는 거리 (1이면 3×3). */
    static final int RADIUS = 1;

    @Override
    public char symbol() {
        return SYMBOL;
    }

    @Override
    public void onLock(ItemContext context, Position position) {
        previewOnLock(context, position);
    }

    @Override
    public void previewOnLock(ItemContext context, Position position) {
        for (int dy = -RADIUS; dy <= RADIUS; dy++) {
            for (int dx = -RADIUS; dx <= RADIUS; dx++) {
                int x = position.x() + dx;
                int y = position.y() + dy;
                if (x >= 0 && x < context.width() && y >= 0 && y < context.height()) {
                    context.removeCell(new Position(x, y));
                }
            }
        }
    }
}
