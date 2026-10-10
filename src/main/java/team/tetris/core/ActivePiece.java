package team.tetris.core;

import java.util.Objects;
import team.tetris.core.item.Item;

/**
 * 현재 낙하 중인 블록의 상태 - 어떤 블록인지(모양과 아이템), 어떤 회전상태인지, 보드 위 어디(origin)에 있는지.
 */
public record ActivePiece(Piece piece, int rotation, Position origin) {

    public ActivePiece {
        Objects.requireNonNull(piece, "piece");
        Objects.requireNonNull(origin, "origin");
    }

    /** 아이템이 없는 블록. */
    public ActivePiece(Shape shape, int rotation, Position origin) {
        this(Piece.of(shape), rotation, origin);
    }

    public Shape shape() {
        return piece.shape();
    }

    /** 각 칸의 보드 좌표. 인덱스는 {@link Piece#items()}의 칸 인덱스와 같다. */
    public Position[] cells() {
        Position[] offsets = piece.shape().cellsAt(rotation);
        Position[] cells = new Position[offsets.length];
        for (int i = 0; i < offsets.length; i++) {
            cells[i] = origin.translate(offsets[i].x(), offsets[i].y());
        }
        return cells;
    }

    /** cellIndex 칸의 아이템. 없으면 null. */
    public Item itemAt(int cellIndex) {
        return piece.itemAt(cellIndex);
    }

    /** 같은 블록을 다른 위치로 옮긴 상태. */
    public ActivePiece movedTo(Position nextOrigin) {
        return new ActivePiece(piece, rotation, nextOrigin);
    }

    /** 같은 블록을 다른 회전 상태·위치로 바꾼 상태. */
    public ActivePiece rotatedTo(int nextRotation, Position nextOrigin) {
        return new ActivePiece(piece, nextRotation, nextOrigin);
    }
}
