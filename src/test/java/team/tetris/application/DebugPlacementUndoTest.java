package team.tetris.application;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import team.tetris.application.debug.DebugItem;
import team.tetris.application.debug.DebugSession;
import team.tetris.application.debug.DebugSetup;
import team.tetris.application.model.Difficulty;
import team.tetris.application.model.GameMode;
import team.tetris.bootstrap.AppComposition;
import team.tetris.core.Piece;
import team.tetris.core.TetrominoType;
import team.tetris.core.item.DoubleScoreItem;

class DebugPlacementUndoTest {
    @TempDir Path directory;

    enum Drop { HARD, SOFT, STEP, AUTOMATIC }

    private DebugSession start(DebugSetup setup) {
        return (DebugSession) new AppComposition(directory, true).newDebugGame(setup, GameMode.ITEM).session();
    }

    private DebugSession undo(DebugSession original) {
        var restored = start(original.setup());
        original.replayBeforeLastPlacement(restored);
        assertTrue(restored.frozen());
        return restored;
    }

    private static void drop(DebugSession session, Drop drop) {
        switch (drop) {
            case HARD -> session.handle(GameCommand.HARD_DROP);
            case SOFT -> session.handle(GameCommand.SOFT_DROP);
            case STEP -> session.stepGravity();
            case AUTOMATIC -> {
                session.setFrozen(false);
                session.update(1_000_000_000L);
            }
        }
    }

    @ParameterizedTest @EnumSource(Drop.class)
    void everyLockPathCanBeUndoneAndReplayed(Drop drop) {
        var original = start(DebugSetup.empty(Difficulty.NORMAL));
        GameSnapshot before = original.snapshot();
        for (int i = 0; i < 30 && !original.canUndoPlacement(); i++) {
            before = original.snapshot();
            drop(original, drop);
        }
        assertTrue(original.canUndoPlacement());
        var after = original.snapshot();
        var restored = undo(original);
        assertSnapshotEquals(before, restored.snapshot());
        assertFalse(restored.canUndoPlacement());
        drop(restored, drop);
        assertSnapshotEquals(after, restored.snapshot());
    }

    @Test void lineClearScoreAndRandomItemThresholdAreRestored() {
        var original = start(new DebugSetup(1, 1, DebugSetup.HolePattern.ALIGNED, 9, Difficulty.NORMAL, 13));
        original.replaceNext(TetrominoType.I, DebugItem.NONE);
        for (int i = 0; i < 10; i++) original.handle(GameCommand.MOVE_RIGHT);
        original.handle(GameCommand.HARD_DROP);
        original.handle(GameCommand.ROTATE_CW);
        original.handle(GameCommand.MOVE_LEFT);
        var before = original.snapshot();
        original.handle(GameCommand.HARD_DROP);
        var after = original.snapshot();
        assertEquals(10, after.clearedLines());
        assertTrue(after.engine().nextPiece().hasItems());

        var restored = undo(original);
        assertSnapshotEquals(before, restored.snapshot());
        restored.handle(GameCommand.HARD_DROP);
        assertSnapshotEquals(after, restored.snapshot());
        // 다음 아이템 이후의 추첨도 같은 난수 위치에서 이어진다.
        original.handle(GameCommand.HARD_DROP);
        restored.handle(GameCommand.HARD_DROP);
        assertSnapshotEquals(original.snapshot(), restored.snapshot());
    }

    @Test void consecutiveUndosPreserveStatsEditsAndNextPieceOverrides() {
        var original = start(DebugSetup.empty(Difficulty.NORMAL));
        original.applyStats(9, Difficulty.HARD);
        var doubleScore = DebugItem.all().stream().filter(item -> item.piece(TetrominoType.I)
                .items().values().stream().anyMatch(DoubleScoreItem.class::isInstance)).findFirst().orElseThrow();
        original.replaceNext(TetrominoType.I, doubleScore);
        original.setFrozen(false);
        original.update(300_000_000L);
        original.setFrozen(true);
        var beforeFirst = original.snapshot();
        original.handle(GameCommand.HARD_DROP);
        original.handle(GameCommand.MOVE_LEFT);
        var beforeSecond = original.snapshot();
        original.handle(GameCommand.HARD_DROP);
        assertEquals(3, original.snapshot().doubleScoreClears());

        var firstUndo = undo(original);
        assertSnapshotEquals(beforeSecond, firstUndo.snapshot());
        assertEquals(Difficulty.HARD, firstUndo.difficulty());
        assertTrue(firstUndo.canUndoPlacement());
        var secondUndo = undo(firstUndo);
        assertSnapshotEquals(beforeFirst, secondUndo.snapshot());
        assertFalse(secondUndo.canUndoPlacement());
        secondUndo.handle(GameCommand.MOVE_RIGHT);
        secondUndo.handle(GameCommand.HARD_DROP);
        assertTrue(secondUndo.canUndoPlacement());
    }

    @Test void gameOverCanBeUndoneWithoutLoggingEveryReplayedCommand() throws Exception {
        var original = start(DebugSetup.empty(Difficulty.NORMAL));
        GameSnapshot before = original.snapshot();
        for (int i = 0; i < 100 && original.result().isEmpty(); i++) {
            before = original.snapshot();
            original.handle(GameCommand.HARD_DROP);
        }
        assertEquals(GameStatus.GAME_OVER, original.snapshot().status());
        var log = directory.resolve("debug/debug.log");
        int logLength = Files.readString(log).length();
        var restored = undo(original);
        assertSnapshotEquals(before, restored.snapshot());
        assertTrue(restored.result().isEmpty());
        String newLog = Files.readString(log).substring(logLength);
        assertTrue(newLog.contains("UNDO_PLACEMENT"));
        assertFalse(newLog.contains("COMMAND HARD_DROP"));
    }

    private static void assertSnapshotEquals(GameSnapshot expected, GameSnapshot actual) {
        assertArrayEquals(expected.engine().board(), actual.engine().board());
        assertEquals(expected.engine().phase(), actual.engine().phase());
        var expectedActive = expected.engine().activePiece();
        var actualActive = actual.engine().activePiece();
        assertEquals(expectedActive.origin(), actualActive.origin());
        assertEquals(expectedActive.rotation(), actualActive.rotation());
        assertPieceEquals(expectedActive.piece(), actualActive.piece());
        assertPieceEquals(expected.engine().nextPiece(), actual.engine().nextPiece());
        assertEquals(expected.score(), actual.score());
        assertEquals(expected.clearedLines(), actual.clearedLines());
        assertEquals(expected.level(), actual.level());
        assertEquals(expected.gravityIntervalNanos(), actual.gravityIntervalNanos());
        assertEquals(expected.doubleScoreClears(), actual.doubleScoreClears());
        assertEquals(expected.status(), actual.status());
    }

    private static void assertPieceEquals(Piece expected, Piece actual) {
        assertEquals(expected.shape(), actual.shape());
        assertEquals(expected.items().keySet(), actual.items().keySet());
        expected.items().forEach((index, item) -> {
            assertEquals(item.getClass(), actual.itemAt(index).getClass());
            assertEquals(item.symbol(), actual.itemAt(index).symbol());
        });
    }
}
