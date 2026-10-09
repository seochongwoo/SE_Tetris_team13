package team.tetris.core;

import java.util.ArrayList;
import java.util.List;
import team.tetris.core.item.Item;
import team.tetris.core.item.ItemContext;

/** core 테스트용 아이템. 실제 아이템(L, 무게추 등)은 아이템 카드에서 따로 구현한다. */
final class TestItems {

    private TestItems() {
    }

    /** 아무 효과 없이 발동만 하는 아이템. */
    static Item marker(char symbol) {
        return new Item() {
            @Override
            public char symbol() {
                return symbol;
            }

            @Override
            public void onLock(ItemContext context, Position position) {
            }
        };
    }

    /** 자기가 놓인 줄을 지우는 아이템 (줄 삭제 아이템과 같은 동작). */
    static Item clearsItsRow() {
        return new Item() {
            @Override
            public char symbol() {
                return 'R';
            }

            @Override
            public void onLock(ItemContext context, Position position) {
                context.clearRow(position.y());
            }
        };
    }

    /** 발동할 때마다 받은 위치와 그 순간 자기 칸의 상태를 기록하는 아이템. */
    static final class Recording implements Item {
        final List<Position> positions = new ArrayList<>();
        final List<Cell> cellsSeen = new ArrayList<>();

        @Override
        public char symbol() {
            return 'Q';
        }

        @Override
        public void onLock(ItemContext context, Position position) {
            positions.add(position);
            cellsSeen.add(context.cellAt(position));
        }
    }
}
