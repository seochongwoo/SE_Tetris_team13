package team.tetris.core.item;

import java.util.Map;
import java.util.random.RandomGenerator;
import team.tetris.core.Piece;
import team.tetris.core.Position;
import team.tetris.core.Shape;
import team.tetris.core.rule.PieceMovementRule;

/** 폭 4칸 무게추. 낙하 중 삭제와 접촉 후 이동 제한은 전용 이동 규칙으로 처리한다. */
public final class WeightBlock implements ItemKind, Item {
    public static final char SYMBOL = 'W';

    /** 위 2칸 / 아래 4칸, 회전하지 않는 전용 모양. */
    public static final Shape SHAPE = new Shape() {
        @Override
        public Position[] cellsAt(int rotation) {
            return new Position[] {
                new Position(1, 0), new Position(2, 0),
                new Position(0, 1), new Position(1, 1), new Position(2, 1), new Position(3, 1)
            };
        }

        @Override
        public int rotationStates() {
            return 1;
        }

        @Override
        public PieceMovementRule movementRule() {
            return WeightMovementRule.INSTANCE;
        }

        @Override
        public String toString() {
            return "WeightBlock.SHAPE";
        }
    };

    @Override
    public Piece toItemPiece(Piece next, RandomGenerator random) {
        return new Piece(SHAPE, Map.of(0, this));
    }

    @Override
    public char symbol() {
        return SYMBOL;
    }

    @Override
    public void onLock(ItemContext context, Position position) {
        // 바닥에서는 아이템 표시만 소모되고 무게추 모양의 일반 칸이 남는다.
    }
}
