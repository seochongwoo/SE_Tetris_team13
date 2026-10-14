package team.tetris.application.debug;

import java.util.Random;
import team.tetris.core.Piece;
import team.tetris.core.TetrominoType;
import team.tetris.core.item.*;

/** 디버그 패널에서 지정할 아이템. 부착 위치도 같은 선택이면 항상 같다. */
public enum DebugItem {
    NONE("없음", null),
    CHAIN("연쇄 삭제", new AttachedItem(new ChainClearItem())),
    BOMB("폭탄", new SoloBlock(new BombItem())),
    DOUBLE("점수 2배", new AttachedItem(new DoubleScoreItem()));

    private final String label;
    private final ItemKind kind;

    DebugItem(String label, ItemKind kind) {
        this.label = label;
        this.kind = kind;
    }

    public Piece piece(TetrominoType type) {
        Piece plain = Piece.of(type);
        return kind == null ? plain : kind.toItemPiece(plain, new Random(13));
    }

    @Override public String toString() { return label; }
}
