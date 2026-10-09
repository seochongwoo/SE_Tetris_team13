package team.tetris.core;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import team.tetris.core.item.Item;

/**
 * 보드에 나올 블록 하나: 모양과, 몇 번째 칸에 어떤 아이템이 실렸는지.
 *
 * <p>items의 키는 {@link Shape#cellsAt}이 돌려주는 칸 인덱스다. 모양은 회전해도 같은 인덱스가
 * 같은 칸을 가리키므로, 아이템은 블록과 함께 회전한다. 아이템이 없는 일반 블록은 {@link #of}로 만든다.
 */
public record Piece(Shape shape, Map<Integer, Item> items) {

    public Piece {
        Objects.requireNonNull(shape, "shape");
        items = Map.copyOf(items);
        int cells = shape.cellCount();
        for (Integer index : items.keySet()) {
            if (index < 0 || index >= cells) {
                throw new IllegalArgumentException("Item index " + index + " is outside the " + cells + " cells");
            }
        }
    }

    /** 아이템이 없는 블록. */
    public static Piece of(Shape shape) {
        return new Piece(shape, Map.of());
    }

    /** cellIndex 칸에 item을 더 실은 블록. */
    public Piece withItem(int cellIndex, Item item) {
        Map<Integer, Item> next = new HashMap<>(items);
        next.put(cellIndex, Objects.requireNonNull(item, "item"));
        return new Piece(shape, next);
    }

    /** cellIndex 칸의 아이템. 없으면 null. */
    public Item itemAt(int cellIndex) {
        return items.get(cellIndex);
    }

    public boolean hasItems() {
        return !items.isEmpty();
    }
}
