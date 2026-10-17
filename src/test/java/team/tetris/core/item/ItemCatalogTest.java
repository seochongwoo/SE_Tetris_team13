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
            // 무게추처럼 여러 칸에 같은 아이템이 실린 블록도 아이템 하나로 센다.
            piece.items().values().stream().map(Item::symbol).distinct().forEach(symbols::add);
        }
        return symbols;
    }

    @Test
    void containsTheTeamItemsEachWithItsOwnLetter() {
        List<Character> symbols = symbols();

        assertEquals(ItemCatalog.all().size(), symbols.size());
        assertEquals(symbols.size(), new HashSet<>(symbols).size(), "아이템마다 다른 문자");
        assertTrue(symbols.containsAll(Set.of('C', 'B', '$', 'W')));
    }
}
