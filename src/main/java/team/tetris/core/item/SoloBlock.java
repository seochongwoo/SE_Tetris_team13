package team.tetris.core.item;

import java.util.Map;
import java.util.Objects;
import java.util.random.RandomGenerator;
import team.tetris.core.Piece;
import team.tetris.core.Position;
import team.tetris.core.Shape;

/**
 * 블록 자체가 아이템인 1칸짜리 블록으로 등장하는 방식 (예: 폭탄). 원래 나올 일반 블록 대신 나온다.
 * 1칸이라 회전하지 않는다.
 */
public record SoloBlock(Item item) implements ItemKind {

    /** 1칸짜리 모양. 회전 상태가 하나뿐이라 회전 시도는 항상 실패한다. */
    public static final Shape SHAPE = new Shape() {
        @Override
        public Position[] cellsAt(int rotation) {
            return new Position[] {new Position(0, 0)};
        }

        @Override
        public int rotationStates() {
            return 1;
        }

        @Override
        public String toString() {
            return "SoloBlock.SHAPE";
        }
    };

    public SoloBlock {
        Objects.requireNonNull(item, "item");
    }

    @Override
    public Piece toItemPiece(Piece next, RandomGenerator random) {
        return new Piece(SHAPE, Map.of(0, item));
    }
}
