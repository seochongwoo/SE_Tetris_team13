package team.tetris.core.item;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import org.junit.jupiter.api.Test;
import team.tetris.core.Piece;
import team.tetris.core.TetrominoType;

class ItemCatalogTest {

    /** 목록의 아이템 종류마다 아이템 블록을 하나씩 만들어 그 문자를 모은다. */
    private static List<Character> symbols() {
        List<Character> symbols = new ArrayList<>();
        Random random = new Random(3);
        for (ItemKind kind : ItemCatalog.all()) {
            Piece piece = kind.toItemPiece(Piece.of(TetrominoType.L), random);
            assertTrue(piece.hasItems(), kind + " must produce an item piece");
            piece.items().values().forEach(item -> symbols.add(item.symbol()));
        }
        return symbols;
    }

    @Test
    void containsAllFiveItemsEachWithItsOwnLetter() {
        List<Character> symbols = symbols();

        assertEquals(ItemCatalog.all().size(), symbols.size());
        assertEquals(symbols.size(), new HashSet<>(symbols).size(), "아이템마다 다른 문자");
        assertEquals(Set.of('L', 'W', 'C', 'B', '$'), new HashSet<>(symbols));
    }
}
