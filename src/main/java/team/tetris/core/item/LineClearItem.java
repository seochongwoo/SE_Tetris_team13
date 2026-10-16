package team.tetris.core.item;

import team.tetris.core.Position;

/** 일반 블록의 한 칸에 붙어 고정 시 해당 줄을 지우는 아이템. */
public final class LineClearItem implements Item {
    public static final char SYMBOL = 'L';

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
        context.clearRow(position.y());
    }
}
