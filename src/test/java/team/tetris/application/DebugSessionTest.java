package team.tetris.application;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import team.tetris.application.debug.*;
import team.tetris.application.model.GameMode;
import team.tetris.application.model.Difficulty;
import team.tetris.application.model.Settings;
import team.tetris.bootstrap.AppComposition;
import team.tetris.core.*;
import team.tetris.core.rule.PlainPieceSource;
import team.tetris.core.rule.SevenBagGenerator;

class DebugSessionTest {
    @TempDir Path directory;

    private DebugSession start(DebugSetup setup) {
        return (DebugSession) new AppComposition(directory, true).newDebugGame(setup, GameMode.ITEM).session();
    }

    @Test void frozenGravityStillAllowsMovementAndStepUsesRealScoring() {
        DebugSession session = start(DebugSetup.empty(Difficulty.NORMAL));
        Position before = session.snapshot().engine().activePiece().origin();
        session.update(Long.MAX_VALUE);
        assertEquals(before, session.snapshot().engine().activePiece().origin());
        session.handle(GameCommand.MOVE_LEFT);
        session.stepGravity();
        assertEquals(before.translate(-1, 1), session.snapshot().engine().activePiece().origin());
        assertEquals(1, session.snapshot().score());
        session.handle(GameCommand.PAUSE);
        session.stepGravity();
        assertEquals(1, session.snapshot().score());
        assertEquals(GameStatus.PAUSED, session.snapshot().status());
    }

    @Test void togglingGravityDiscardsOldElapsedTime() {
        DebugSession session = start(DebugSetup.empty(Difficulty.NORMAL));
        session.setFrozen(false);
        session.update(900_000_000);
        session.setFrozen(true);
        session.update(Long.MAX_VALUE);
        session.setFrozen(false);
        session.update(100_000_000);
        assertEquals(0, session.snapshot().score());
        session.update(900_000_000);
        assertEquals(1, session.snapshot().score());
    }

    @Test void aSingleStepCanLockClearAndScoreFourLines() {
        DebugSession session = start(new DebugSetup(4, 1, DebugSetup.HolePattern.ALIGNED, 0, Difficulty.NORMAL, 13));
        prepareIInGap(session);
        for (int i = 0; i < 16; i++) session.stepGravity();
        long before = session.snapshot().score();
        session.stepGravity();
        assertEquals(4, session.snapshot().clearedLines());
        assertEquals(1600, session.snapshot().score() - before);
    }

    @Test void ninthLinePresetSynchronizesSpeedAndItemThreshold() {
        DebugSession session = start(new DebugSetup(1, 1, DebugSetup.HolePattern.ALIGNED, 9, Difficulty.NORMAL, 13));
        prepareIInGap(session);
        assertEquals(9, session.snapshot().clearedLines());
        assertFalse(isItem(session.snapshot().engine().nextPiece()));
        Piece preview = session.snapshot().engine().nextPiece();
        session.handle(GameCommand.HARD_DROP);
        assertEquals(10, session.snapshot().clearedLines());
        assertEquals(1, session.snapshot().level());
        assertEquals(900_000_000, session.snapshot().gravityIntervalNanos());
        assertEquals(preview, session.snapshot().engine().activePiece().piece());
        assertTrue(isItem(session.snapshot().engine().nextPiece()));
        assertTrue(session.snapshot().score() >= 100);
    }

    @ParameterizedTest @EnumSource(DebugItem.class)
    void selectedItemImmediatelyMatchesPreviewAndNextSpawn(DebugItem item) {
        DebugSession session = start(DebugSetup.empty(Difficulty.NORMAL));
        session.replaceNext(TetrominoType.I, item);
        Piece preview = session.snapshot().engine().nextPiece();
        assertEquals(item.piece(TetrominoType.I), preview);
        session.handle(GameCommand.HARD_DROP);
        assertEquals(preview, session.snapshot().engine().activePiece().piece());
        session.handle(GameCommand.HARD_DROP);
        assertEquals(GameStatus.RUNNING, session.snapshot().status());
        if (item == DebugItem.DOUBLE) assertEquals(3, session.snapshot().doubleScoreClears());
    }

    @ParameterizedTest @EnumSource(DebugSetup.HolePattern.class)
    void everySetupCanRestartWithIdenticalBoardAndRandomSequence(DebugSetup.HolePattern pattern) {
        DebugSetup setup = new DebugSetup(5, 3, pattern, 9, Difficulty.HARD, 987);
        DebugSession first = start(setup);
        DebugSession second = start(setup);
        assertArrayEquals(first.snapshot().engine().board(), second.snapshot().engine().board());
        for (int i = 0; i < 15; i++) {
            Piece firstNext = first.snapshot().engine().nextPiece();
            Piece secondNext = second.snapshot().engine().nextPiece();
            assertEquals(firstNext.shape(), secondNext.shape());
            assertEquals(firstNext.items().keySet(), secondNext.items().keySet());
            firstNext.items().forEach((index, item) ->
                    assertEquals(item.symbol(), secondNext.itemAt(index).symbol()));
            first.handle(GameCommand.HARD_DROP);
            second.handle(GameCommand.HARD_DROP);
            assertEquals(first.snapshot().score(), second.snapshot().score());
            assertEquals(first.snapshot().status(), second.snapshot().status());
        }
    }

    @Test void gameOverEndsThroughNormalSpawnCollision() {
        DebugSession session = start(DebugSetup.empty(Difficulty.NORMAL));
        finish(session);
        assertEquals(GameStatus.GAME_OVER, session.snapshot().status());
        assertEquals(GameStatus.GAME_OVER, session.result().orElseThrow().reason());
        long score = session.snapshot().score();
        session.stepGravity();
        session.update(Long.MAX_VALUE);
        assertEquals(score, session.snapshot().score());
    }

    @Test void debugStorageIsIsolatedAndNormalCompositionRejectsDebugScenarios() throws Exception {
        var normal = new AppComposition(directory);
        normal.settings().update(Settings.defaults());
        String original = Files.readString(directory.resolve("settings.properties"));
        var debug = new AppComposition(directory, true);
        debug.settings().update(Settings.defaults());
        assertTrue(Files.exists(directory.resolve("debug/settings.properties")));
        DebugSession session = (DebugSession) debug.newDebugGame(DebugSetup.empty(Difficulty.NORMAL), GameMode.ITEM).session();
        finish(session);
        GameResult result = session.result().orElseThrow();
        debug.endings().begin(result);
        debug.endings().submitName(result.gameId(), "debug");
        assertTrue(Files.exists(directory.resolve("debug/scores.bin")));
        assertFalse(Files.exists(directory.resolve("scores.bin")));
        assertEquals(original, Files.readString(directory.resolve("settings.properties")));
        assertFalse(normal.debugEnabled());
        assertInstanceOf(SinglePlayerSession.class, normal.newGame().session());
        assertThrows(IllegalStateException.class, () -> normal.newDebugGame(DebugSetup.empty(Difficulty.NORMAL), GameMode.NORMAL));
    }

    @Test void initialBoardIsCopiedAndInvalidActivePlacementIsRejected() {
        Cell[][] cells = new DebugSetup(4, 1, DebugSetup.HolePattern.ALIGNED, 0, Difficulty.NORMAL, 13).board();
        var source = new PlainPieceSource(new SevenBagGenerator(13));
        var engine = new PlayerEngine(cells, new ActivePiece(TetrominoType.I, 1, new Position(2, 16)), source);
        cells[19][0] = Cell.EMPTY;
        assertFalse(engine.snapshot().board()[19][0].isEmpty());
        assertThrows(IllegalArgumentException.class, () -> new PlayerEngine(cells,
                new ActivePiece(TetrominoType.O, 0, new Position(0, 18)), source));
    }

    private static void prepareIInGap(DebugSession session) {
        session.replaceNext(TetrominoType.I, DebugItem.NONE);
        for (int i = 0; i < 10; i++) session.handle(GameCommand.MOVE_RIGHT);
        session.handle(GameCommand.HARD_DROP);
        session.handle(GameCommand.ROTATE_CW);
        session.handle(GameCommand.MOVE_LEFT);
    }

    @Test void statEditsPreserveBoardPiecesAndScoreAndSynchronizeNextItemThreshold() {
        DebugSession session = start(new DebugSetup(1, 1, DebugSetup.HolePattern.ALIGNED, 0, Difficulty.NORMAL, 13));
        prepareIInGap(session);
        GameSnapshot before = session.snapshot();
        session.applyStats(29, Difficulty.HARD);
        assertArrayEquals(before.engine().board(), session.snapshot().engine().board());
        assertEquals(before.engine().activePiece(), session.snapshot().engine().activePiece());
        assertEquals(before.engine().nextPiece(), session.snapshot().engine().nextPiece());
        assertEquals(before.score(), session.snapshot().score());
        assertEquals(2, session.snapshot().level());
        assertEquals(760_000_000L, session.snapshot().gravityIntervalNanos());
        session.handle(GameCommand.HARD_DROP);
        assertEquals(30, session.snapshot().clearedLines());
        assertEquals(640_000_000L, session.snapshot().gravityIntervalNanos());
        assertTrue(isItem(session.snapshot().engine().nextPiece()));
        session.handle(GameCommand.QUIT_GAME);
        assertEquals(Difficulty.HARD, session.result().orElseThrow().difficulty());
    }

    @Test void invalidStatEditsLeaveTheSessionUnchanged() {
        DebugSession session = start(DebugSetup.empty(Difficulty.NORMAL));
        assertThrows(IllegalArgumentException.class, () -> session.applyStats(-1, Difficulty.HARD));
        assertThrows(IllegalArgumentException.class, () -> session.applyStats(DebugSetup.MAX_LINES + 1, Difficulty.HARD));
        assertEquals(Difficulty.NORMAL, session.difficulty());
        assertEquals(0, session.snapshot().clearedLines());
    }

    @Test void logIncludesEachGravityStepButNoIdleFramesAndRecordsCommandsAndFullState() throws Exception {
        DebugSession session = start(DebugSetup.empty(Difficulty.NORMAL));
        Path log = directory.resolve("debug/debug.log");
        String initial = Files.readString(log);
        for (int i = 0; i < 20; i++) {
            session.update(16_000_000);
            session.snapshot();
        }
        assertEquals(initial, Files.readString(log));
        session.setFrozen(false);
        session.update(3_100_000_000L);
        String gravity = Files.readString(log);
        assertEquals(3, gravity.lines().filter(line -> line.endsWith("] GRAVITY")).count());
        assertTrue(gravity.contains("score=1 "));
        assertTrue(gravity.contains("score=2 "));
        assertTrue(gravity.contains("score=3 "));
        session.applyStats(9, Difficulty.HARD);
        session.replaceNext(TetrominoType.I, DebugItem.DOUBLE);
        session.handle(GameCommand.HARD_DROP);
        session.handle(GameCommand.HARD_DROP);
        session.handle(GameCommand.PAUSE);
        session.handle(GameCommand.RESUME);
        session.handle(GameCommand.QUIT_GAME);
        String text = Files.readString(log);
        for (String marker : new String[]{"APPLY_SETUP", "APPLY_STATS", "REPLACE_NEXT", "COMMAND HARD_DROP",
                "COMMAND PAUSE", "COMMAND RESUME", "status=ABORTED", "difficulty=HARD", "next=", "active=",
                "doubleScoreClears=3", "source=totalLines=9", "items=[DoubleScoreItem", "00|", "19|"}) {
            assertTrue(text.contains(marker), marker);
        }
    }

    private static void finish(DebugSession session) {
        for (int i = 0; i < 100 && session.result().isEmpty(); i++) session.handle(GameCommand.HARD_DROP);
        assertTrue(session.result().isPresent());
    }

    private static boolean isItem(Piece piece) {
        return piece.hasItems() || !(piece.shape() instanceof TetrominoType);
    }
}
