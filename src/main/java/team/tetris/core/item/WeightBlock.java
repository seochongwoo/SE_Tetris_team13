package team.tetris.core.item;

import java.util.HashMap;
import java.util.Map;
import java.util.random.RandomGenerator;
import team.tetris.core.Piece;
import team.tetris.core.Position;
import team.tetris.core.Shape;

/**
 * 무게추 블록으로 등장하는 방식. 원래 나올 일반 블록 대신 아래 모양의 너비 4칸 블록이 나오고, 모든
 * 칸에 {@link WeightItem}이 실린다. 회전 상태가 하나라 회전하지 않는다.
 *
 * <pre>
 *  WW
 * WWWW
 * </pre>
 */
public final class WeightBlock implements ItemKind {

    /** 너비 4칸 무게추 모양. 위 2칸, 아래 4칸. */
    public static final Shape SHAPE = new Shape() {
        private final Position[] cells = {
                new Position(1, 0), new Position(2, 0),
                new Position(0, 1), new Position(1, 1), new Position(2, 1), new Position(3, 1),
        };

        @Override
        public Position[] cellsAt(int rotation) {
            return cells.clone();
        }

        @Override
        public int rotationStates() {
            return 1;
        }

        @Override
        public String toString() {
            return "WeightBlock.SHAPE";
        }
    };

    private final WeightItem item = new WeightItem();

    @Override
    public Piece toItemPiece(Piece next, RandomGenerator random) {
        Map<Integer, Item> items = new HashMap<>();
        for (int i = 0; i < SHAPE.cellCount(); i++) {
            items.put(i, item);
        }
        return new Piece(SHAPE, items);
    }
}
