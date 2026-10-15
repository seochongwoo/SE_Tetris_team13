package team.tetris.application.debug;

import java.util.List;
import java.util.Random;
import java.util.stream.Stream;
import team.tetris.core.Piece;
import team.tetris.core.TetrominoType;
import team.tetris.core.item.ItemCatalog;
import team.tetris.core.item.ItemKind;

/** 디버그 패널에서 지정할 아이템. 부착 위치도 같은 선택이면 항상 같다. */
public record DebugItem(String label, ItemKind kind) {
    public static final DebugItem NONE = new DebugItem("없음", null);

    /** 일반 게임과 같은 카탈로그를 사용하므로 아이템별 디버그 등록은 필요 없다. */
    public static List<DebugItem> all() {
        return Stream.concat(Stream.of(NONE), ItemCatalog.entries().stream()
                .map(entry -> new DebugItem(entry.label(), entry.kind()))).toList();
    }

    public Piece piece(TetrominoType type) {
        Piece plain = Piece.of(type);
        return kind == null ? plain : kind.toItemPiece(plain, new Random(13));
    }

    @Override public String toString() { return label; }
}
