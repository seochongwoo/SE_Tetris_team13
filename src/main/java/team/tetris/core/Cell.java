package team.tetris.core;

/**
 * 보드 한 칸의 불변 상태.
 *
 * 비어있으면 ind == EMPTY이고 occupiedBy == null 이다.
 * 채워져 있으면 kind == OCCUPIED이고 occupiedBy에 그 칸을 채운
 * 블록 종류가 담긴다 (UI가 색상을 매핑할 때 사용). 색상 값 자체는 여기서 다루지 않는다.
 */
public record Cell(CellKind kind, TetrominoType occupiedBy) {

    public static final Cell EMPTY = new Cell(CellKind.EMPTY, null);

    public Cell {
        if (kind == CellKind.OCCUPIED && occupiedBy == null) {
            throw new IllegalArgumentException("occupiedBy must not be null for an OCCUPIED cell");
        }
        if (kind == CellKind.EMPTY && occupiedBy != null) {
            throw new IllegalArgumentException("occupiedBy must be null for an EMPTY cell");
        }
    }

    public static Cell occupiedBy(TetrominoType type) {
        return new Cell(CellKind.OCCUPIED, type);
    }

    public boolean isEmpty() {
        return kind == CellKind.EMPTY;
    }
}
