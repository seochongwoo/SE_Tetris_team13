package team.tetris.core.rule;

import java.util.Objects;
import team.tetris.core.Piece;

/** 아이템 없이 생성기가 정한 모양만 그대로 내놓는 공급자 (일반 모드). */
public final class PlainPieceSource implements PieceSource {

    private final PieceGenerator generator;

    public PlainPieceSource(PieceGenerator generator) {
        this.generator = Objects.requireNonNull(generator, "generator");
    }

    @Override
    public Piece next() {
        return Piece.of(generator.next());
    }

    @Override
    public Piece peek() {
        return Piece.of(generator.peek());
    }
}
