package team.tetris.ui.screen;

import static org.junit.jupiter.api.Assertions.*;
import static team.tetris.ui.TestApplication.FRAME;

import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import team.tetris.application.GameStatus;
import team.tetris.application.LineClearAnimation;
import team.tetris.application.SinglePlayerSession;
import team.tetris.application.StartedGame;
import team.tetris.application.model.LoadResult;
import team.tetris.application.model.Settings;
import team.tetris.core.ActivePiece;
import team.tetris.core.Board;
import team.tetris.core.Cell;
import team.tetris.core.EnginePhase;
import team.tetris.core.Piece;
import team.tetris.core.PlayerEngine;
import team.tetris.core.Position;
import team.tetris.core.TetrominoType;
import team.tetris.core.item.LineClearItem;
import team.tetris.core.rule.PieceSource;
import team.tetris.ui.ScreenRouter;
import team.tetris.ui.TestApplication;

class AnimationInputTest {
    private final ScreenRouter router = new TestApplication().router();

    private GameScreen newGame() {
        PieceSource source = new PieceSource() {
            public Piece next() { return peek(); }
            public Piece peek() { return Piece.of(TetrominoType.I).withItem(0, new LineClearItem()); }
        };
        var session = new SinglePlayerSession(new PlayerEngine(10, 20, source));
        return new GameScreen(router, new StartedGame(session,
                new LoadResult<>(Settings.defaults(), Optional.empty())));
    }

    private GameScreen clearingGame() {
        GameScreen game = newGame();
        tap(game, "SPACE");
        game.update(FRAME);
        assertNotNull(game.session().snapshot().lineClearAnimation());
        return game;
    }

    private static void tap(GameScreen game, String key) {
        game.onKeyPressed(key);
        game.onKeyReleased(key);
    }

    @Test
    void bufferedInputsRunInOrderBeforeInputsOnTheFinalAnimationFrame() {
        GameScreen game = clearingGame();
        var before = game.session().snapshot();
        // 왼쪽 벽을 넘는 네 번째 이동은 실패해야 한다. 마지막 RIGHT가 먼저 실행되면 결과가 달라진다.
        for (int i = 0; i < 4; i++) tap(game, "LEFT");
        game.update(LineClearAnimation.DURATION_NANOS - 1);
        assertEquals(before.engine().activePiece(), game.session().snapshot().engine().activePiece());
        assertEquals(before.score(), game.session().snapshot().score());

        tap(game, "RIGHT");
        tap(game, "UP");
        tap(game, "DOWN");
        game.update(1);
        var after = game.session().snapshot();
        assertNull(after.lineClearAnimation());
        assertEquals(new Position(1, 1), after.engine().activePiece().origin());
        assertEquals(1, after.engine().activePiece().rotation());
        assertEquals(before.score() + 1, after.score());
        game.update(FRAME);
        assertEquals(after.engine().activePiece(), game.session().snapshot().engine().activePiece());
        assertEquals(after.score(), game.session().snapshot().score());
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void heldRepeatsAreBufferedIncludingTheFinalFrame(boolean releaseBeforeEnd) {
        GameScreen game = clearingGame();
        var before = game.session().snapshot();
        game.onKeyPressed("DOWN");
        game.update(170_000_000);
        game.update(50_000_000);
        assertEquals(before.engine().activePiece(), game.session().snapshot().engine().activePiece());
        if (releaseBeforeEnd) game.onKeyReleased("DOWN");
        game.update(80_000_000);
        game.onKeyReleased("DOWN");

        int steps = releaseBeforeEnd ? 3 : 4; // 최초 입력 + 170/220ms 반복 (+ 마지막 프레임의 270ms 반복)
        var after = game.session().snapshot();
        assertNull(after.lineClearAnimation());
        assertEquals(before.engine().activePiece().origin().translate(0, steps),
                after.engine().activePiece().origin());
        assertEquals(before.score() + steps, after.score());
    }

    @Test
    void commandsQueuedBehindTheDropThatStartsAnimationArePreserved() {
        GameScreen game = newGame();
        tap(game, "SPACE");
        tap(game, "RIGHT");
        tap(game, "UP");
        game.update(FRAME);
        var before = game.session().snapshot();
        assertNotNull(before.lineClearAnimation());
        assertEquals(0, before.engine().activePiece().rotation());

        game.update(LineClearAnimation.DURATION_NANOS);
        var after = game.session().snapshot();
        assertEquals(before.engine().activePiece().origin().translate(1, 0), after.engine().activePiece().origin());
        assertEquals(1, after.engine().activePiece().rotation());
    }

    @Test
    void bufferedHardDropsCanStartAnotherAnimationWithoutLosingFollowingCommands() {
        GameScreen game = clearingGame();
        tap(game, "RIGHT");
        tap(game, "SPACE");
        tap(game, "LEFT");
        tap(game, "SPACE");
        tap(game, "RIGHT");
        game.update(LineClearAnimation.DURATION_NANOS - 1);
        assertEquals(1, game.session().snapshot().clearedLines());
        game.update(1);
        var second = game.session().snapshot();
        assertEquals(2, second.clearedLines());
        assertEquals(0, second.lineClearAnimation().elapsedNanos());
        assertEquals(TetrominoType.I, second.lineClearAnimation().rows().getFirst().cells().get(7).occupiedBy());
        assertTrue(second.lineClearAnimation().rows().getFirst().cells().get(3).isEmpty());

        game.update(LineClearAnimation.DURATION_NANOS);
        var third = game.session().snapshot();
        assertEquals(3, third.clearedLines());
        assertEquals(0, third.lineClearAnimation().elapsedNanos());
        assertEquals(TetrominoType.I, third.lineClearAnimation().rows().getFirst().cells().get(2).occupiedBy());
        assertTrue(third.lineClearAnimation().rows().getFirst().cells().get(6).isEmpty());

        game.update(LineClearAnimation.DURATION_NANOS);
        var after = game.session().snapshot();
        assertNull(after.lineClearAnimation());
        assertEquals(new Position(4, 0), after.engine().activePiece().origin());
        assertEquals(3, after.clearedLines());
        game.update(FRAME);
        assertEquals(after.score(), game.session().snapshot().score());
    }

    @ParameterizedTest @ValueSource(strings = {"P", "ESCAPE", "FOCUS_LOST"})
    void pauseOrFocusLossDiscardsBufferedCommandsAndHeldRepeats(String pauseTrigger) {
        GameScreen game = clearingGame();
        var before = game.session().snapshot();
        tap(game, "RIGHT");
        tap(game, "UP");
        tap(game, "SPACE");
        game.onKeyPressed("DOWN");
        game.update(FRAME);
        if (pauseTrigger.equals("FOCUS_LOST")) game.onFocusLost();
        else tap(game, pauseTrigger);
        game.update(FRAME);
        assertEquals(GameStatus.PAUSED, game.session().snapshot().status());

        tap(game, "P");
        game.update(FRAME);
        game.update(LineClearAnimation.DURATION_NANOS);
        var after = game.session().snapshot();
        assertEquals(GameStatus.RUNNING, after.status());
        assertNull(after.lineClearAnimation());
        assertEquals(before.engine().activePiece(), after.engine().activePiece());
        assertEquals(before.score(), after.score());
        game.update(200_000_000);
        assertEquals(after.engine().activePiece(), game.session().snapshot().engine().activePiece());
    }

    @Test
    void gameOverAtTheEndOfAnimationDiscardsInputsAndKeepsTheFinalScore() {
        Cell[][] board = new Board(10, 20).snapshot();
        board[0][4] = Cell.occupiedBy(TetrominoType.Z);
        PieceSource source = new PieceSource() {
            public Piece next() { return peek(); }
            public Piece peek() { return Piece.of(TetrominoType.T); }
        };
        var piece = Piece.of(TetrominoType.I).withItem(0, new LineClearItem());
        var session = new SinglePlayerSession(new PlayerEngine(board,
                new ActivePiece(piece, 0, new Position(3, 18)), source));
        var game = new GameScreen(router, new StartedGame(session,
                new LoadResult<>(Settings.defaults(), Optional.empty())));
        tap(game, "SPACE");
        game.update(FRAME);
        assertEquals(EnginePhase.GAME_OVER, session.snapshot().engine().phase());
        assertNotNull(session.snapshot().lineClearAnimation());

        tap(game, "LEFT");
        tap(game, "UP");
        tap(game, "SPACE");
        game.onKeyPressed("DOWN");
        game.update(LineClearAnimation.DURATION_NANOS - 1);
        assertTrue(session.result().isEmpty());
        tap(game, "RIGHT");
        game.update(1);
        assertEquals(GameStatus.GAME_OVER, session.result().orElseThrow().reason());
        assertEquals(100, session.result().orElseThrow().score());
        assertEquals(1, session.result().orElseThrow().clearedLines());
        assertNull(session.snapshot().lineClearAnimation());

        game.update(GameScreen.GAME_OVER_DELAY_NANOS);
        var nameEntry = assertInstanceOf(NameEntryScreen.class, router.current());
        assertEquals("", nameEntry.name());
        assertEquals(100, session.result().orElseThrow().score());
    }
}
