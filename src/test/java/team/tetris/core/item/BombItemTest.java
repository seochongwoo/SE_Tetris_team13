package team.tetris.core.item;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import team.tetris.core.Position;

class BombItemTest {

    private final BombItem item = new BombItem();
    private final BoardContext context = new BoardContext(5, 5);

    private void fillBoard() {
        for (int y = 0; y < 5; y++) {
            for (int x = 0; x < 5; x++) {
                context.place(SoloBlock.SHAPE, new Position(x, y));
            }
        }
    }

    @Test
    void usesTheLetterB() {
        assertEquals('B', item.symbol());
    }

    @Test
    void removesTheThreeByThreeAreaAroundItself() {
        fillBoard();

        item.onLock(context, new Position(2, 2));

        assertEquals(25 - 9, context.filledCells());
        for (int y = 1; y <= 3; y++) {
            for (int x = 1; x <= 3; x++) {
                assertTrue(context.cellAt(new Position(x, y)).isEmpty(), x + "," + y);
            }
        }
        assertFalse(context.cellAt(new Position(0, 2)).isEmpty());
        assertFalse(context.cellAt(new Position(2, 4)).isEmpty());
    }

    @Test
    void theAreaIsClippedAtTheBoardCorner() {
        fillBoard();

        item.onLock(context, new Position(4, 4));

        assertEquals(25 - 4, context.filledCells());
        assertFalse(context.cellAt(new Position(2, 4)).isEmpty());
    }

    @Test
    void theAreaIsClippedAtTheBoardEdge() {
        fillBoard();

        item.onLock(context, new Position(0, 2));

        assertEquals(25 - 6, context.filledCells());
        assertEquals(0, context.clearRowCalls);
    }
}
