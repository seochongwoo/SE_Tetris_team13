package team.tetris.core;

import team.tetris.core.item.Item;

/**
 * 보드 한 칸의 불변 상태.
 *
 * <p>비어 있으면 occupiedBy와 item이 모두 null이다. 채워져 있으면 occupiedBy에 그 칸을 채운
 * 블록의 모양이 담긴다 (UI가 색상·무늬를 고를 때 사용). 아이템이 실린 칸이면 item도 채워진다.
 * 색상 값 자체는 여기서 다루지 않는다.
 */
public record Cell(Shape occupiedBy, Item item) {

    public static final Cell EMPTY = new Cell(null, null);

    public Cell {
        if (occupiedBy == null && item != null) {
            throw new IllegalArgumentException("An empty cell cannot hold an item");
        }
    }

    public static Cell occupiedBy(Shape shape) {
        if (shape == null) {
            throw new IllegalArgumentException("occupiedBy must not be null for an occupied cell");
        }
        return new Cell(shape, null);
    }

    public static Cell withItem(Shape shape, Item item) {
        if (shape == null) {
            throw new IllegalArgumentException("occupiedBy must not be null for an occupied cell");
        }
        return new Cell(shape, item);
    }

    public boolean isEmpty() {
        return occupiedBy == null;
    }

    public boolean hasItem() {
        return item != null;
    }

    /** 아이템 표시만 지운 같은 칸. */
    public Cell withoutItem() {
        return item == null ? this : new Cell(occupiedBy, null);
    }
}
