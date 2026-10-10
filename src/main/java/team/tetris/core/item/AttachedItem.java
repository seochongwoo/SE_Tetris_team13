package team.tetris.core.item;

import java.util.Objects;
import java.util.random.RandomGenerator;
import team.tetris.core.Piece;

/** 일반 블록의 무작위 한 칸에 붙는 아이템 (요구사항: 블록에 붙는 위치는 무작위). */
public record AttachedItem(Item item) implements ItemKind {

    public AttachedItem {
        Objects.requireNonNull(item, "item");
    }

    @Override
    public Piece toItemPiece(Piece next, RandomGenerator random) {
        return next.withItem(random.nextInt(next.shape().cellCount()), item);
    }
}
