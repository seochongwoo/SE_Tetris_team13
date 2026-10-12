package team.tetris.core.item;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Random;
import org.junit.jupiter.api.Test;
import team.tetris.core.Piece;
import team.tetris.core.Position;
import team.tetris.core.TetrominoType;

class SoloBlockTest {

    @Test
    void replacesTheNextPieceWithAOneCellItemBlock() {
        BombItem bomb = new BombItem();

        Piece piece = new SoloBlock(bomb).toItemPiece(Piece.of(TetrominoType.I), new Random(1));

        assertSame(SoloBlock.SHAPE, piece.shape());
        assertEquals(1, piece.shape().cellCount());
        assertSame(bomb, piece.itemAt(0));
    }

    @Test
    void theShapeHasOneCellAndDoesNotRotate() {
        assertEquals(1, SoloBlock.SHAPE.rotationStates());
        for (int rotation = -1; rotation <= 4; rotation++) {
            assertEquals(new Position(0, 0), SoloBlock.SHAPE.cellsAt(rotation)[0]);
        }
    }

    @Test
    void requiresAnItem() {
        assertThrows(NullPointerException.class, () -> new SoloBlock(null));
    }

    @Test
    void attachedItemsGoOnARandomCellOfTheNormalPiece() {
        AttachedItem kind = new AttachedItem(new DoubleScoreItem());
        Random random = new Random(7);
        boolean[] seen = new boolean[4];

        for (int i = 0; i < 200; i++) {
            Piece piece = kind.toItemPiece(Piece.of(TetrominoType.T), random);
            assertEquals(TetrominoType.T, piece.shape());
            assertEquals(1, piece.items().size());
            seen[piece.items().keySet().iterator().next()] = true;
        }

        for (boolean cell : seen) {
            assertTrue(cell);
        }
    }
}
