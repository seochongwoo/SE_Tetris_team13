package team.tetris.application;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import team.tetris.core.*;
import team.tetris.core.item.*;
import team.tetris.core.rule.PieceSource;

class ItemClearAnimationTest {
    private static SinglePlayerSession game(Cell[][] board, Piece piece, Position origin) {
        return new SinglePlayerSession(new PlayerEngine(board, new ActivePiece(piece, 0, origin), new PieceSource() {
            public Piece next() { return Piece.of(TetrominoType.T); }
            public Piece peek() { return Piece.of(TetrominoType.T); }
        }));
    }

    private static Set<Position> removed(GameSnapshot snapshot) {
        return snapshot.lineClearAnimation().removedCells().stream()
                .map(cell -> cell.position()).collect(Collectors.toSet());
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void bombRestoresOnlyDeletedCellsAndDefersGameOver(boolean gameOver) {
        Cell[][] board = new Board(10, 20).snapshot();
        for (int x = 2; x <= 5; x++) board[18][x] = Cell.occupiedBy(TetrominoType.O);
        if (gameOver) board[0][4] = Cell.occupiedBy(TetrominoType.Z);
        var game = game(board, new SoloBlock(new BombItem()).toItemPiece(null, null), new Position(4, 17));

        game.handle(GameCommand.HARD_DROP);

        var first = game.snapshot();
        assertEquals(Set.of(new Position(4, 17), new Position(3, 18), new Position(4, 18), new Position(5, 18)), removed(first));
        assertTrue(first.lineClearAnimation().rows().isEmpty());
        assertEquals(0, first.score());
        assertEquals(0, first.clearedLines());
        assertEquals(GameStatus.RUNNING, first.status());
        assertTrue(game.result().isEmpty());
        var restored = first.lineClearAnimation().boardBeforeClear(first.engine().board());
        for (int x = 2; x <= 5; x++) assertEquals(TetrominoType.O, restored[18][x].occupiedBy());
        assertEquals(SoloBlock.SHAPE, restored[17][4].occupiedBy());
        restored[18][3] = Cell.EMPTY;
        assertEquals(TetrominoType.O, first.lineClearAnimation().boardBeforeClear(first.engine().board())[18][3].occupiedBy());
        assertThrows(UnsupportedOperationException.class, () -> first.lineClearAnimation().removedCells().clear());

        game.update(LineClearAnimation.DURATION_NANOS - 1);
        assertNotNull(game.snapshot().lineClearAnimation());
        assertTrue(game.result().isEmpty());
        game.update(1);
        assertNull(game.snapshot().lineClearAnimation());
        assertEquals(gameOver ? GameStatus.GAME_OVER : GameStatus.RUNNING, game.snapshot().status());
        assertEquals(TetrominoType.O, game.snapshot().engine().board()[18][2].occupiedBy());
        for (int x = 3; x <= 5; x++) assertTrue(game.snapshot().engine().board()[18][x].isEmpty());
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void chainAnimatesOnlyWhenItActuallyDeletesCells(boolean connected) {
        Cell[][] board = new Board(10, 20).snapshot();
        if (connected) board[19][3] = Cell.occupiedBy(TetrominoType.O);
        board[19][6] = Cell.occupiedBy(TetrominoType.T);
        var game = game(board, Piece.of(TetrominoType.O).withItem(0, new ChainClearItem()), new Position(3, 18));
        game.handle(GameCommand.HARD_DROP);
        if (connected) {
            assertEquals(5, removed(game.snapshot()).size());
            assertFalse(removed(game.snapshot()).contains(new Position(6, 19)));
            assertEquals(0, game.snapshot().clearedLines());
            assertEquals(0, game.snapshot().score());
            game.update(LineClearAnimation.DURATION_NANOS);
        }
        assertNull(game.snapshot().lineClearAnimation());
        assertEquals(TetrominoType.T, game.snapshot().engine().board()[19][6].occupiedBy());
    }

    @Test void doubleScoreWithoutDeletionHasNoAnimation() {
        var game = game(new Board(10, 20).snapshot(),
                Piece.of(TetrominoType.O).withItem(0, new DoubleScoreItem()), new Position(3, 18));
        game.handle(GameCommand.HARD_DROP);
        assertNull(game.snapshot().lineClearAnimation());
        assertEquals(SinglePlayerSession.DOUBLE_SCORE_CLEARS, game.snapshot().doubleScoreClears());
    }
}
