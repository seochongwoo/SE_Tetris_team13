package team.tetris.core.rule;

import java.util.Objects;
import team.tetris.core.Piece;

/** 다음 블록 하나만 교체한다. 실제 공급자의 줄 집계와 난수 진행은 유지한다. */
public final class DebugPieceSource implements PieceSource {
    private final PieceSource base;
    private Piece replacement;

    public DebugPieceSource(PieceSource base) {
        this.base = Objects.requireNonNull(base, "base");
    }

    public void replaceNext(Piece piece) {
        replacement = Objects.requireNonNull(piece, "piece");
    }

    @Override public Piece peek() {
        return replacement == null ? base.peek() : replacement;
    }

    @Override public Piece next() {
        Piece result = peek();
        base.next();
        replacement = null;
        return result;
    }

    @Override public void onLinesCleared(int lines) {
        base.onLinesCleared(lines);
    }
}
