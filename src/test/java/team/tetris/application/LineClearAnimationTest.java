package team.tetris.application;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import team.tetris.core.*;
import team.tetris.core.item.LineClearItem;
import team.tetris.core.result.ClearedRow;
import team.tetris.core.rule.PieceSource;

class LineClearAnimationTest {
    private static SinglePlayerSession game(boolean item, boolean gameOver) {
        Cell[][] board = new Board(10, 20).snapshot();
        if (!item) {
            for (int x = 0; x < 10; x++) {
                if (x < 3 || x > 6) board[19][x] = Cell.occupiedBy(TetrominoType.O);
            }
        }
        board[18][0] = Cell.occupiedBy(TetrominoType.J);
        if (gameOver) board[0][4] = Cell.occupiedBy(TetrominoType.Z);
        Piece piece = Piece.of(TetrominoType.I);
        if (item) piece = piece.withItem(0, new LineClearItem());
        PieceSource source = new PieceSource() {
            public Piece next() { return Piece.of(TetrominoType.T); }
            public Piece peek() { return Piece.of(TetrominoType.T); }
        };
        return new SinglePlayerSession(new PlayerEngine(board,
                new ActivePiece(piece, 0, new Position(3, 18)), source));
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void normalAndItemClearsFreezeInputThenGiveTheNextPieceAFullInterval(boolean item) {
        var session = game(item, false);
        session.handle(GameCommand.HARD_DROP);
        GameSnapshot first = session.snapshot();
        assertEquals(100, first.score());
        assertEquals(1, first.clearedLines());
        assertTrue(first.lineClearAnimation().flashing());
        assertEquals(19, first.lineClearAnimation().rows().getFirst().rowIndex());
        Cell[][] restored = first.lineClearAnimation().boardBeforeClear(first.engine().board());
        assertEquals(TetrominoType.J, restored[18][0].occupiedBy());
        assertEquals(TetrominoType.I, restored[19][3].occupiedBy());

        for (GameCommand command : List.of(GameCommand.MOVE_LEFT, GameCommand.MOVE_RIGHT,
                GameCommand.ROTATE_CW, GameCommand.SOFT_DROP, GameCommand.HARD_DROP)) {
            session.handle(command);
        }
        session.stepGravity();
        assertSame(first, session.snapshot());
        session.update(LineClearAnimation.FLASH_NANOS - 1);
        assertTrue(session.snapshot().lineClearAnimation().flashing());
        session.update(1);
        assertFalse(session.snapshot().lineClearAnimation().flashing());
        assertEquals(0, first.lineClearAnimation().elapsedNanos());
        session.update(LineClearAnimation.DURATION_NANOS - LineClearAnimation.FLASH_NANOS - 1);
        assertNotNull(session.snapshot().lineClearAnimation());
        session.update(1);
        assertNull(session.snapshot().lineClearAnimation());
        assertEquals(first.engine().activePiece(), session.snapshot().engine().activePiece());
        assertEquals(100, session.snapshot().score());
        assertEquals(TetrominoType.J, session.snapshot().engine().board()[19][0].occupiedBy());
        session.update(session.snapshot().gravityIntervalNanos() - 1);
        assertEquals(first.engine().activePiece(), session.snapshot().engine().activePiece());
        session.update(1);
        assertEquals(first.engine().activePiece().origin().translate(0, 1),
                session.snapshot().engine().activePiece().origin());
    }

    @Test void gravityClearAndLongFrameDoNotDropTheNextPieceDuringAnimation() {
        var session = game(false, false);
        session.update(Long.MAX_VALUE);
        var before = session.snapshot();
        assertNotNull(before.lineClearAnimation());
        assertEquals(0, before.lineClearAnimation().elapsedNanos());
        session.update(Long.MAX_VALUE);
        assertNull(session.snapshot().lineClearAnimation());
        assertEquals(before.engine().activePiece(), session.snapshot().engine().activePiece());
        assertEquals(0, session.accumulatedGravityNanos());
        assertEquals(before.score(), session.snapshot().score());
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void pausePreservesRemainingAnimationAndDefersGameOverUntilItFinishes(boolean gameOver) {
        var session = game(false, gameOver);
        session.handle(GameCommand.HARD_DROP);
        session.update(100_000_000);
        session.handle(GameCommand.PAUSE);
        assertEquals(GameStatus.PAUSED, session.snapshot().status());
        var paused = session.snapshot();
        session.update(Long.MAX_VALUE);
        session.handle(GameCommand.HARD_DROP);
        assertSame(paused, session.snapshot());
        assertTrue(session.result().isEmpty());
        session.handle(GameCommand.RESUME);
        session.update(199_999_999);
        assertTrue(session.result().isEmpty());
        assertEquals(GameStatus.RUNNING, session.snapshot().status());
        session.update(1);
        assertNull(session.snapshot().lineClearAnimation());
        if (gameOver) {
            assertEquals(GameStatus.GAME_OVER, session.snapshot().status());
            assertEquals(100, session.result().orElseThrow().score());
            var result = session.result().orElseThrow();
            session.update(Long.MAX_VALUE);
            session.handle(GameCommand.HARD_DROP);
            assertSame(result, session.result().orElseThrow());
        } else {
            assertEquals(EnginePhase.RUNNING, session.snapshot().engine().phase());
            assertTrue(session.result().isEmpty());
        }
    }

    @Test void quittingDuringPausedAnimationCancelsItAndKeepsTheAwardedScore() {
        var session = game(false, false);
        session.handle(GameCommand.HARD_DROP);
        session.handle(GameCommand.PAUSE);
        session.handle(GameCommand.QUIT_GAME);
        assertNull(session.snapshot().lineClearAnimation());
        assertEquals(GameStatus.ABORTED, session.result().orElseThrow().reason());
        assertEquals(100, session.result().orElseThrow().score());
    }

    @Test void restoresSeparatedRowsAndTopRowWithoutSharingMutableArrays() {
        Cell[][] cells = new Board(4, 6).snapshot();
        for (int y = 0; y < cells.length; y++) cells[y][y % 4] = Cell.occupiedBy(TetrominoType.values()[y]);
        Board board = new Board(cells);
        var clear = board.clearRows(Set.of(0, 2, 4));
        var rows = new ArrayList<>(clear.clearedRows());
        var animation = new LineClearAnimation(rows, 0);
        rows.clear();
        var compacted = board.snapshot();
        var restored = animation.boardBeforeClear(compacted);
        assertArrayEquals(cells, restored);
        restored[1][1] = Cell.EMPTY;
        restored[0][0] = Cell.EMPTY;
        assertArrayEquals(cells, animation.boardBeforeClear(compacted));
        assertArrayEquals(board.snapshot(), compacted);
        assertThrows(UnsupportedOperationException.class, () -> animation.rows().clear());
    }

    @Test void rejectsEmptyRowsAndTimeOutsideTheAnimation() {
        assertThrows(IllegalArgumentException.class, () -> new LineClearAnimation(List.of(), 0));
        var rows = List.of(new ClearedRow(0, List.of(Cell.EMPTY)));
        assertThrows(IllegalArgumentException.class, () -> new LineClearAnimation(rows, -1));
        assertThrows(IllegalArgumentException.class,
                () -> new LineClearAnimation(rows, LineClearAnimation.DURATION_NANOS));
    }
}
