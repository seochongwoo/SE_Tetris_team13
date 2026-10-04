package team.tetris.application;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import team.tetris.application.EndGameView.Stage;
import team.tetris.application.model.ScoreStore;
import team.tetris.application.port.ScoreRepository;
import team.tetris.application.port.StorageException;
import team.tetris.storage.memory.InMemoryScoreRepository;

class EndGameCoordinatorTest {
    private final ControlledRepository repository = new ControlledRepository();
    private final ScoreboardService scores = new ScoreboardService(repository);
    private final EndGameCoordinator coordinator = new EndGameCoordinator(scores);

    private static GameResult game(long score) {
        return new GameResult(UUID.randomUUID(), score, 0, 0, GameStatus.GAME_OVER);
    }

    @Test
    void naturalEndRequestsNameThenReturnsImmutableScoresAndHighlightOnce() {
        GameResult result = game(100);
        EndGameView initial = coordinator.begin(result);
        assertEquals(Stage.NAME_REQUIRED, initial.stage());
        assertSame(initial, coordinator.begin(result));
        EndGameView completed = coordinator.submitName(result.gameId(), " player ");
        assertEquals(Stage.SHOW_SCOREBOARD, completed.stage());
        assertEquals("player", completed.scores().getFirst().name());
        assertEquals(completed.scores().getFirst().recordId(), completed.highlightedRecordId().orElseThrow());
        assertThrows(UnsupportedOperationException.class, () -> completed.scores().clear());
        assertSame(completed, coordinator.submitName(result.gameId(), "other"));
        assertSame(completed, coordinator.begin(result));
        assertEquals(1, repository.saves);
    }

    @Test
    void abortedGameReturnsMenuWithoutReadingOrWritingStorage() {
        repository.failRead = true;
        GameResult result = new GameResult(UUID.randomUUID(), 50, 1, 10, GameStatus.ABORTED);
        EndGameView view = coordinator.begin(result);
        assertEquals(Stage.RETURN_MENU, view.stage());
        assertTrue(view.storageError().isEmpty());
        assertSame(view, coordinator.submitName(result.gameId(), "ignored"));
        assertEquals(0, repository.loads);
        assertEquals(0, repository.saves);
    }

    @Test
    void nonQualifyingResultSkipsNameAndPendingNameRechecksCutoff() throws Exception {
        for (int i = 0; i < 10; i++) scores.register(game(10 + i), "old");
        GameResult low = game(10);
        EndGameView view = coordinator.begin(low);
        assertEquals(Stage.SHOW_SCOREBOARD, view.stage());
        assertTrue(view.highlightedRecordId().isEmpty());
        GameResult pending = game(11);
        assertEquals(Stage.NAME_REQUIRED, coordinator.begin(pending).stage());
        scores.register(game(100), "new");
        int saves = repository.saves;
        EndGameView rejected = coordinator.submitName(pending.gameId(), "pending");
        assertEquals(Stage.SHOW_SCOREBOARD, rejected.stage());
        assertTrue(rejected.highlightedRecordId().isEmpty());
        assertEquals(saves, repository.saves);
    }

    @Test
    void invalidNameKeepsResultAndAllowsCorrectedSubmission() {
        GameResult result = game(50);
        coordinator.begin(result);
        for (String name : new String[]{null, "", "1234567890123", "a\nb"}) {
            EndGameView invalid = coordinator.submitName(result.gameId(), name);
            assertEquals(Stage.NAME_REQUIRED, invalid.stage());
            assertTrue(invalid.nameError().isPresent());
            assertTrue(invalid.storageError().isEmpty());
        }
        assertEquals(0, repository.saves);
        EndGameView saved = coordinator.submitName(result.gameId(), "correct");
        assertEquals(50, saved.scores().getFirst().score());
        assertTrue(saved.nameError().isEmpty());
    }

    @Test
    void failedWriteKeepsNameStateAndRetrySavesExactlyOnce() {
        GameResult result = game(80);
        coordinator.begin(result);
        repository.failWrite = true;
        EndGameView failed = coordinator.submitName(result.gameId(), "name");
        assertEquals(Stage.NAME_REQUIRED, failed.stage());
        assertEquals(StorageException.Kind.WRITE_FAILED, failed.storageError().orElseThrow().kind());
        assertTrue(failed.highlightedRecordId().isEmpty());
        assertEquals(ScoreStore.empty(), repository.backing.load());
        assertSame(failed, coordinator.begin(result));
        repository.failWrite = false;
        EndGameView saved = coordinator.submitName(result.gameId(), "name");
        assertEquals(Stage.SHOW_SCOREBOARD, saved.stage());
        assertEquals(1, repository.saves);
    }

    @Test
    void failedInitialReadCanBeRetriedWithoutLosingResult() {
        GameResult result = game(80);
        repository.failRead = true;
        EndGameView failed = coordinator.begin(result);
        assertEquals(Stage.CHECKING, failed.stage());
        assertTrue(failed.storageError().isPresent());
        assertThrows(IllegalStateException.class, () -> coordinator.submitName(result.gameId(), "name"));
        repository.failRead = false;
        assertEquals(Stage.NAME_REQUIRED, coordinator.begin(result).stage());
        assertEquals(Stage.SHOW_SCOREBOARD, coordinator.submitName(result.gameId(), "name").stage());
    }

    @Test
    void successfulSaveFollowedByFailedReadRetriesDisplayWithoutSavingAgain() {
        GameResult result = game(90);
        coordinator.begin(result);
        repository.failReadAfterSave = true;
        EndGameView failed = coordinator.submitName(result.gameId(), "name");
        assertEquals(Stage.SHOW_SCOREBOARD, failed.stage());
        assertTrue(failed.storageError().isPresent());
        assertEquals(1, repository.saves);
        assertEquals(1, repository.backing.load().entries().size());
        assertTrue(coordinator.begin(result).storageError().isPresent());
        repository.failRead = false;
        EndGameView retried = coordinator.submitName(result.gameId(), "ignored");
        assertTrue(retried.storageError().isEmpty());
        assertTrue(retried.highlightedRecordId().isPresent());
        assertEquals("name", retried.scores().getFirst().name());
        assertEquals(1, repository.saves);
    }

    @Test
    void recreatedCoordinatorFindsRegistrationAndDoesNotHighlightEvictedRecord() throws Exception {
        GameResult result = game(0);
        UUID id = scores.register(result, "first").orElseThrow();
        assertEquals(id, coordinator.begin(result).highlightedRecordId().orElseThrow());
        for (int i = 1; i <= 10; i++) scores.register(game(i), "higher");
        int saves = repository.saves;
        var recreated = new EndGameCoordinator(scores);
        EndGameView view = recreated.begin(result);
        assertEquals(Stage.SHOW_SCOREBOARD, view.stage());
        assertTrue(view.highlightedRecordId().isEmpty());
        recreated.submitName(result.gameId(), "again");
        assertEquals(saves, repository.saves);
    }

    @Test
    void distinctPendingGamesRemainIndependentAndConflictingIdsAreRejected() {
        GameResult first = game(5);
        GameResult second = game(10);
        coordinator.begin(first);
        coordinator.begin(second);
        coordinator.submitName(second.gameId(), "second");
        EndGameView view = coordinator.submitName(first.gameId(), "first");
        assertEquals(2, view.scores().size());
        assertEquals("second", view.scores().getFirst().name());
        assertThrows(IllegalArgumentException.class, () -> coordinator.begin(
                new GameResult(first.gameId(), 99, 0, 0, GameStatus.GAME_OVER)));
        assertThrows(IllegalArgumentException.class, () -> coordinator.submitName(UUID.randomUUID(), "unknown"));
        assertThrows(NullPointerException.class, () -> coordinator.begin(null));
    }

    private static final class ControlledRepository implements ScoreRepository {
        final InMemoryScoreRepository backing = new InMemoryScoreRepository();
        boolean failRead, failWrite, failReadAfterSave;
        int loads, saves;
        public ScoreStore load() throws StorageException {
            loads++;
            if (failRead) throw new StorageException(StorageException.Kind.READ_FAILED, Path.of("scores.bin"), null);
            return backing.load();
        }
        public void save(ScoreStore store) throws StorageException {
            if (failWrite) throw new StorageException(StorageException.Kind.WRITE_FAILED, Path.of("scores.bin"), null);
            backing.save(store);
            saves++;
            if (failReadAfterSave) failRead = true;
        }
    }
}
