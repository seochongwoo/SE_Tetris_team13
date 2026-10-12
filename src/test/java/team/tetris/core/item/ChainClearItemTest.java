package team.tetris.core.item;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import team.tetris.core.Position;
import team.tetris.core.TetrominoType;

/** O(회전 0)는 origin에서 x+1~x+2, y~y+1 칸을, I(회전 0)는 x~x+3, y+1 칸을 차지한다. */
class ChainClearItemTest {

    private final ChainClearItem item = new ChainClearItem();
    private final BoardContext context = new BoardContext(10, 10);

    @Test
    void usesTheLetterC() {
        assertEquals('C', item.symbol());
    }

    @Test
    void removesEveryConnectedCellOfTheSameKindButLeavesOtherKinds() {
        context.place(TetrominoType.O, new Position(-1, 8)); // 아이템 블록: (0~1, 8~9)
        context.place(TetrominoType.O, new Position(1, 8));  // 오른쪽에 맞닿음
        context.place(TetrominoType.O, new Position(-1, 6)); // 위에 맞닿음
        Position[] other = context.place(TetrominoType.I, new Position(4, 8)); // 오른쪽 O에 맞닿은 다른 종류

        item.onLock(context, new Position(0, 8));

        assertEquals(4, context.filledCells());
        for (Position cell : other) {
            assertEquals(TetrominoType.I, context.cellAt(cell).occupiedBy());
        }
        assertEquals(0, context.clearRowCalls);
    }

    @Test
    void doesNothingWhenOnlyItsOwnPieceHasThatKind() {
        context.place(TetrominoType.O, new Position(-1, 8));
        context.place(TetrominoType.I, new Position(2, 8));

        item.onLock(context, new Position(0, 9));

        assertEquals(8, context.filledCells());
    }

    @Test
    void diagonalNeighborsAreNotConnected() {
        context.place(TetrominoType.O, new Position(-1, 8)); // (0~1, 8~9)
        context.place(TetrominoType.O, new Position(1, 6));  // (2~3, 6~7): 모서리만 닿음

        item.onLock(context, new Position(1, 8));

        assertEquals(8, context.filledCells());
    }

    @Test
    void ignoresCellsThatAreNotTetrominoes() {
        context.place(SoloBlock.SHAPE, new Position(0, 9));
        context.place(SoloBlock.SHAPE, new Position(1, 9));

        item.onLock(context, new Position(0, 9));

        assertEquals(2, context.filledCells());
    }

    @Test
    void doesNothingWhenItsCellIsAlreadyEmpty() {
        item.onLock(context, new Position(5, 5));

        assertTrue(context.cellAt(new Position(5, 5)).isEmpty());
        assertEquals(0, context.filledCells());
    }
}
