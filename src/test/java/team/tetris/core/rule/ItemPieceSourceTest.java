package team.tetris.core.rule;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import org.junit.jupiter.api.Test;
import team.tetris.core.GameAction;
import team.tetris.core.Piece;
import team.tetris.core.PlayerEngine;
import team.tetris.core.Position;
import team.tetris.core.TetrominoType;
import team.tetris.core.item.AttachedItem;
import team.tetris.core.item.Item;
import team.tetris.core.item.ItemContext;
import team.tetris.core.item.ItemKind;
import team.tetris.core.result.EngineStep;

class ItemPieceSourceTest {

    private static Item item(char symbol) {
        return new Item() {
            @Override
            public char symbol() {
                return symbol;
            }

            @Override
            public void onLock(ItemContext context, Position position) {
            }
        };
    }

    private static final Item MARK = item('M');

    private static PieceSource plain(TetrominoType type) {
        return new PlainPieceSource(new PieceGenerator() {
            @Override
            public TetrominoType next() {
                return type;
            }

            @Override
            public TetrominoType peek() {
                return type;
            }
        });
    }

    private static ItemPieceSource source(List<ItemKind> kinds) {
        return new ItemPieceSource(plain(TetrominoType.T), kinds, new Random(1L));
    }

    @Test
    void noItemAppearsBeforeTenLinesAreCleared() {
        ItemPieceSource source = source(List.of(new AttachedItem(MARK)));

        source.onLinesCleared(4);
        source.onLinesCleared(4);
        source.onLinesCleared(1);

        for (int i = 0; i < 5; i++) {
            assertFalse(source.next().hasItems());
        }
        assertFalse(source.peek().hasItems());
    }

    @Test
    void reachingTenLinesPutsAnItemOnTheNextNewlyShownPreview() {
        ItemPieceSource source = source(List.of(new AttachedItem(MARK)));
        source.onLinesCleared(4);
        source.onLinesCleared(4);

        source.onLinesCleared(2); // 누적 10줄
        Piece spawned = source.next(); // 이미 보이던 미리보기 블록이 나온다

        assertFalse(spawned.hasItems());
        Piece preview = source.peek();
        assertTrue(preview.items().containsValue(MARK));
        assertSame(preview, source.next());
        assertFalse(source.peek().hasItems());
    }

    @Test
    void passingSeveralThresholdsAtOnceGivesThatManyItemsInARow() {
        ItemPieceSource source = source(List.of(new AttachedItem(MARK)));

        source.onLinesCleared(25); // 10, 20을 한 번에 넘김
        source.next();

        assertTrue(source.next().hasItems());
        assertTrue(source.next().hasItems());
        assertFalse(source.next().hasItems());
    }

    @Test
    void thresholdsCountTheWholeGameNotEachClear() {
        ItemPieceSource source = source(List.of(new AttachedItem(MARK)));
        int items = 0;

        for (int clear = 0; clear < 30; clear++) {
            source.onLinesCleared(1);
            source.next();
            if (source.peek().hasItems()) {
                items++;
            }
        }

        assertEquals(3, items);
    }

    @Test
    void withoutAnyItemKindsOnlyPlainPiecesAppear() {
        ItemPieceSource source = source(List.of());

        source.onLinesCleared(20);

        for (int i = 0; i < 5; i++) {
            assertFalse(source.next().hasItems());
        }
    }

    @Test
    void itemsArePickedEvenlyFromTheKinds() {
        Item first = item('A');
        Item second = item('B');
        ItemPieceSource source = source(List.of(new AttachedItem(first), new AttachedItem(second)));
        Set<Item> seen = new HashSet<>();

        for (int i = 0; i < 40; i++) {
            source.onLinesCleared(10);
            source.next();
            seen.addAll(source.peek().items().values());
        }

        assertEquals(Set.of(first, second), seen);
    }

    @Test
    void anAttachedItemLandsOnARandomCellOfTheBlock() {
        AttachedItem kind = new AttachedItem(MARK);
        Random random = new Random(3L);
        Set<Integer> cells = new HashSet<>();

        for (int i = 0; i < 200; i++) {
            cells.addAll(kind.toItemPiece(Piece.of(TetrominoType.I), random).items().keySet());
        }

        assertEquals(Set.of(0, 1, 2, 3), cells);
    }

    @Test
    void negativeLineCountsAreRejected() {
        ItemPieceSource source = source(List.of());

        assertThrows(IllegalArgumentException.class, () -> source.onLinesCleared(-1));
    }

    @Test
    void inAGameTheTenthClearedLineShowsAnItemBlockInThePreview() {
        // 폭4 보드에 가로 I를 떨어뜨리면 매번 한 줄이 지워진다.
        ItemPieceSource source =
                new ItemPieceSource(plain(TetrominoType.I), List.of(new AttachedItem(MARK)), new Random(5L));
        PlayerEngine engine = new PlayerEngine(4, 6, source);

        for (int drop = 1; drop < 10; drop++) {
            EngineStep step = engine.apply(GameAction.HARD_DROP);
            assertEquals(1, step.clearResult().lineCount());
            assertFalse(step.snapshot().nextPiece().hasItems(), "after " + drop + " lines");
        }
        EngineStep tenth = engine.apply(GameAction.HARD_DROP);

        assertTrue(tenth.snapshot().nextPiece().hasItems());
        assertFalse(tenth.snapshot().activePiece().piece().hasItems());

        // 지금 떨어지는 일반 블록 다음에 미리보기의 아이템 블록이 나와, 고정될 때 아이템이 발동한다.
        assertTrue(engine.apply(GameAction.HARD_DROP).itemActivations().isEmpty());
        EngineStep itemDrop = engine.apply(GameAction.HARD_DROP);
        assertEquals(1, itemDrop.itemActivations().size());
        assertSame(MARK, itemDrop.itemActivations().get(0).item());
    }
}
