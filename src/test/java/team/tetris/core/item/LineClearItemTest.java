package team.tetris.core.item;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import team.tetris.core.Cell;
import team.tetris.core.Position;

class LineClearItemTest {

    private final LineClearItem item = new LineClearItem();
    private final BoardContext context = new BoardContext(10, 10);

    @Test
    void usesTheLetterL() {
        assertEquals('L', item.symbol());
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 4, 9})
    void lockingRequestsOnlyItsOwnIncompleteRowWithoutChangingTheBoard(int row) {
        Position position = new Position(2, row);
        context.place(SoloBlock.SHAPE, position);
        context.place(SoloBlock.SHAPE, new Position(7, (row + 1) % 10));
        Cell[][] before = context.board.snapshot();

        item.onLock(context, position);

        assertEquals(List.of(row), context.requestedRows);
        // 실제 삭제와 압축은 엔진이 모든 아이템 효과를 처리한 뒤 수행한다.
        assertArrayEquals(before, context.board.snapshot());
    }

    @Test
    void previewRequestsTheSameRowAsLocking() {
        Position position = new Position(2, 7);
        context.place(SoloBlock.SHAPE, position);
        Cell[][] before = context.board.snapshot();

        item.previewOnLock(context, position);

        assertEquals(List.of(7), context.requestedRows);
        assertArrayEquals(before, context.board.snapshot());
    }
}
