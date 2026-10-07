package team.tetris.application;

import static org.junit.jupiter.api.Assertions.*;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import team.tetris.application.model.*;
import team.tetris.application.port.*;
import team.tetris.storage.memory.*;

class PersistenceServicesTest {
    private static GameResult game(long score) {
        return new GameResult(UUID.randomUUID(), score, 0, 0, GameStatus.GAME_OVER);
    }

    @Test
    void settingsFirstUseUpdateAndResetAreIndependentOfScores() throws Exception {
        var repository = new InMemorySettingsRepository();
        var settings = new SettingsService(repository);
        var scores = new ScoreboardService(new InMemoryScoreRepository());
        assertEquals(Settings.defaults(), settings.get());
        assertTrue(repository.load().isEmpty());
        for (Settings.ScreenSize size : Settings.ScreenSize.values()) {
            var changed = new Settings(size, Settings.defaults().keyBindings(), true);
            settings.update(changed);
            assertEquals(changed, new SettingsService(repository).get());
        }
        scores.register(game(10), "first");
        settings.reset();
        assertEquals(Settings.defaults(), settings.get());
        assertEquals(1, scores.list().size());
        var changed = new Settings(Settings.ScreenSize.LARGE, Settings.defaults().keyBindings(), true);
        settings.update(changed);
        scores.clear();
        assertEquals(changed, settings.get());
        assertTrue(scores.list().isEmpty());
    }

    @Test
    void validatesKeysWithinRunningAndPausedContextsAndDefensivelyCopies() {
        var keys = new EnumMap<>(Settings.defaults().keyBindings());
        assertEquals(keys.get(GameCommand.PAUSE), keys.get(GameCommand.RESUME));
        var settings = new Settings(Settings.ScreenSize.SMALL, keys, false);
        keys.put(GameCommand.MOVE_LEFT, "A");
        assertEquals("LEFT", settings.keyBindings().get(GameCommand.MOVE_LEFT));
        assertThrows(UnsupportedOperationException.class, () -> settings.keyBindings().clear());
        keys.put(GameCommand.MOVE_RIGHT, "A");
        assertThrows(IllegalArgumentException.class, () -> new Settings(Settings.ScreenSize.SMALL, keys, false));
        keys.put(GameCommand.MOVE_RIGHT, "RIGHT");
        keys.put(GameCommand.RESUME, "ESCAPE");
        assertThrows(IllegalArgumentException.class, () -> new Settings(Settings.ScreenSize.SMALL, keys, false));
        keys.put(GameCommand.RESUME, "P");
        keys.put(GameCommand.MOVE_LEFT, "bad key");
        assertThrows(IllegalArgumentException.class, () -> new Settings(Settings.ScreenSize.SMALL, keys, false));
        keys.remove(GameCommand.MOVE_LEFT);
        assertThrows(IllegalArgumentException.class, () -> new Settings(Settings.ScreenSize.SMALL, keys, false));
    }

    @Test
    void customDefaultsAreInjectedAndUsedByReset() throws Exception {
        var defaults = new Settings(Settings.ScreenSize.SMALL, Settings.defaults().keyBindings(), true);
        var service = new SettingsService(new InMemorySettingsRepository(), defaults);
        assertEquals(defaults, service.get());
        service.update(Settings.defaults());
        service.reset();
        assertEquals(defaults, service.get());
    }

    @Test
    void sortsTrimsAndRejectsEqualCutoffWhileRecheckingAfterNameEntry() throws Exception {
        var scores = new ScoreboardService(new InMemoryScoreRepository());
        for (int i = 1; i <= 10; i++) scores.register(game(i), "name");
        assertEquals(List.of(10L,9L,8L,7L,6L,5L,4L,3L,2L,1L),
                scores.list().stream().map(ScoreEntry::score).toList());
        assertFalse(scores.qualifies(game(1)));
        assertTrue(scores.register(game(1), "tie").isEmpty());
        var pending = game(2);
        assertTrue(scores.qualifies(pending));
        scores.register(game(20), "winner");
        assertTrue(scores.register(pending, "pending").isEmpty());
        assertEquals(10, scores.list().size());
        assertThrows(UnsupportedOperationException.class, () -> scores.list().clear());
    }

    @Test
    void tiesRetainRegistrationOrderEvenWhenClockMovesBackwards() throws Exception {
        var repository = new InMemoryScoreRepository();
        var first = new ScoreboardService(repository, ScoreboardPolicy.defaults(),
                Clock.fixed(Instant.ofEpochSecond(100), ZoneOffset.UTC));
        UUID id = first.register(game(10), "first").orElseThrow();
        var later = new ScoreboardService(repository, ScoreboardPolicy.defaults(),
                Clock.fixed(Instant.EPOCH, ZoneOffset.UTC));
        later.register(game(10), "second");
        assertEquals(id, later.list().getFirst().recordId());
        assertTrue(later.list().getFirst().registeredAt().isAfter(later.list().getLast().registeredAt()));
    }

    @Test
    void zeroScoreEntersEmptyRankingAndAbortedGameDoesNot() throws Exception {
        var scores = new ScoreboardService(new InMemoryScoreRepository());
        assertTrue(scores.qualifies(game(0)));
        assertTrue(scores.register(game(0), "zero").isPresent());
        var aborted = new GameResult(UUID.randomUUID(), 100, 1, 10, GameStatus.ABORTED);
        assertFalse(scores.qualifies(aborted));
        assertTrue(scores.register(aborted, "quit").isEmpty());
    }

    @Test
    void validatesUnicodeNamesAndAllowsReplacingRankingPolicy() throws Exception {
        var policy = ScoreboardPolicy.defaults();
        assertEquals("이름", policy.normalizeName("  이름  "));
        assertEquals("😀".repeat(12), policy.normalizeName("😀".repeat(12)));
        for (String invalid : List.of("", "   ", "a\nb", "\tname", "😀".repeat(13))) {
            assertThrows(IllegalArgumentException.class, () -> policy.normalizeName(invalid));
        }
        assertThrows(IllegalArgumentException.class, () -> new ScoreboardPolicy(9, 12, false));
        var service = new ScoreboardService(new InMemoryScoreRepository(),
                new ScoreboardPolicy(12, 20, true), Clock.systemUTC());
        for (int i = 0; i < 12; i++) {
            service.register(new GameResult(UUID.randomUUID(), i, 0, 0, GameStatus.ABORTED), "longer than twelve");
        }
        assertEquals(12, service.list().size());
    }

    @Test
    void duplicatesReturnOriginalIdAfterEvictionAndServiceRecreation() throws Exception {
        var repository = new InMemoryScoreRepository();
        var scores = new ScoreboardService(repository);
        var first = game(0);
        UUID id = scores.register(first, "first").orElseThrow();
        assertEquals(id, scores.register(first, "renamed").orElseThrow());
        assertEquals("first", scores.list().getFirst().name());
        for (int i = 1; i <= 10; i++) scores.register(game(i), "new");
        var reopened = new ScoreboardService(repository);
        assertFalse(reopened.qualifies(first));
        assertEquals(id, reopened.register(first, "again").orElseThrow());
        assertTrue(reopened.list().stream().noneMatch(e -> e.recordId().equals(id)));
        reopened.clear();
        assertTrue(repository.load().registrations().isEmpty());
    }

    @Test
    void failedScoreSaveAndClearPreserveDataAndAllowRetry() throws Exception {
        var backing = new InMemoryScoreRepository();
        boolean[] fail = {true};
        ScoreRepository repository = new ScoreRepository() {
            public ScoreStore load() { return backing.load(); }
            public void save(ScoreStore store) throws StorageException {
                if (fail[0]) throw failure(StorageException.Kind.WRITE_FAILED);
                backing.save(store);
            }
        };
        var service = new ScoreboardService(repository);
        var result = game(100);
        assertThrows(StorageException.class, () -> service.register(result, "name"));
        assertEquals(ScoreStore.empty(), backing.load());
        fail[0] = false;
        UUID id = service.register(result, "name").orElseThrow();
        fail[0] = true;
        assertEquals(id, service.register(result, "again").orElseThrow());
        assertThrows(StorageException.class, service::clear);
        assertEquals(1, service.list().size());
    }

    @Test
    void failedSettingsSaveAndResetPreserveLastCommittedValue() throws Exception {
        var backing = new InMemorySettingsRepository();
        boolean[] fail = {false};
        SettingsRepository repository = new SettingsRepository() {
            public Optional<Settings> load() { return backing.load(); }
            public void save(Settings value) throws StorageException {
                if (fail[0]) throw failure(StorageException.Kind.WRITE_FAILED);
                backing.save(value);
            }
        };
        var service = new SettingsService(repository);
        var changed = new Settings(Settings.ScreenSize.LARGE, Settings.defaults().keyBindings(), true);
        service.update(changed);
        fail[0] = true;
        assertThrows(StorageException.class, service::reset);
        assertThrows(StorageException.class, () -> service.update(Settings.defaults()));
        assertEquals(changed, service.get());
        fail[0] = false;
        service.reset();
        assertEquals(Settings.defaults(), service.get());
    }

    @Test
    void readFailuresReturnExplicitFallbackErrorWithoutWriting() {
        var settings = new SettingsService(new SettingsRepository() {
            public Optional<Settings> load() throws StorageException { throw failure(StorageException.Kind.INVALID_DATA); }
            public void save(Settings settings) { fail("must not overwrite damaged data"); }
        });
        assertThrows(StorageException.class, settings::get);
        assertEquals(Settings.defaults(), settings.loadOrDefault().value());
        assertEquals(StorageException.Kind.INVALID_DATA, settings.loadOrDefault().error().orElseThrow().kind());
        var scores = new ScoreboardService(new ScoreRepository() {
            public ScoreStore load() throws StorageException { throw failure(StorageException.Kind.READ_FAILED); }
            public void save(ScoreStore store) { fail("must not overwrite damaged data"); }
        });
        assertThrows(StorageException.class, () -> scores.register(game(10), "name"));
        assertThrows(StorageException.class, () -> scores.qualifies(game(10)));
        assertTrue(scores.loadOrEmpty().value().isEmpty());
        assertEquals(StorageException.Kind.READ_FAILED, scores.loadOrEmpty().error().orElseThrow().kind());
    }

    @Test
    void scoreStoreIsImmutableAndRejectsInconsistentReceipts() {
        UUID game = UUID.randomUUID();
        UUID id = UUID.randomUUID();
        ScoreEntry entry = new ScoreEntry(id, game, "name", 10, Instant.EPOCH);
        var entries = new java.util.ArrayList<>(List.of(entry));
        var receipts = new java.util.HashMap<>(Map.of(game, id));
        ScoreStore store = new ScoreStore(entries, receipts);
        entries.clear();
        receipts.clear();
        assertEquals(1, store.entries().size());
        assertThrows(UnsupportedOperationException.class, () -> store.registrations().clear());
        assertThrows(IllegalArgumentException.class, () -> new ScoreStore(List.of(entry), Map.of()));
        assertThrows(IllegalArgumentException.class, () -> new ScoreStore(List.of(entry, entry), Map.of(game, id)));
        assertThrows(IllegalArgumentException.class,
                () -> new ScoreStore(List.of(), Map.of(game, id, UUID.randomUUID(), id)));
    }

    @Test
    void eachModeAndDifficultyHasItsOwnCapacityCutoffAndEvictionReceipts() throws Exception {
        var repository = new InMemoryScoreRepository();
        var scores = new ScoreboardService(repository);
        for (GameMode mode : GameMode.values()) {
            for (Difficulty difficulty : Difficulty.values()) {
                var first = new GameResult(UUID.randomUUID(), 0, 0, 0, GameStatus.GAME_OVER, mode, difficulty);
                assertTrue(scores.qualifies(first));
                UUID evicted = scores.register(first, "first").orElseThrow();
                for (int i = 1; i <= 10; i++) {
                    var result = new GameResult(UUID.randomUUID(), i, 0, 0, GameStatus.GAME_OVER, mode, difficulty);
                    scores.register(result, "player" + i);
                }
                assertEquals(List.of(10L,9L,8L,7L,6L,5L,4L,3L,2L,1L),
                        scores.list(mode, difficulty).stream().map(ScoreEntry::score).toList());
                var tied = new GameResult(UUID.randomUUID(), 1, 0, 0, GameStatus.GAME_OVER, mode, difficulty);
                assertFalse(scores.qualifies(tied));
                assertTrue(scores.register(tied, "tie").isEmpty());
                assertEquals(evicted, new ScoreboardService(repository).register(first, "retry").orElseThrow());
            }
        }
        assertEquals(60, scores.list().size());
        assertEquals(66, repository.load().registrations().size());
        scores.clear();
        assertEquals(ScoreStore.empty(), repository.load());
    }

    @Test
    void endingShowsOnlyFinishedCategoryAndHighlightsSavedRecord() throws Exception {
        var scores = new ScoreboardService(new InMemoryScoreRepository());
        scores.register(game(10000), "other");
        var result = new GameResult(UUID.randomUUID(), 10, 0, 0, GameStatus.GAME_OVER,
                GameMode.ITEM, Difficulty.EASY);
        var endings = new EndGameCoordinator(scores);
        assertEquals(EndGameView.Stage.NAME_REQUIRED, endings.begin(result).stage());
        EndGameView view = endings.submitName(result.gameId(), "item");
        assertEquals(1, view.scores().size());
        assertEquals(GameMode.ITEM, view.scores().getFirst().mode());
        assertEquals(Difficulty.EASY, view.scores().getFirst().difficulty());
        assertEquals(view.scores().getFirst().recordId(), view.highlightedRecordId().orElseThrow());
        assertEquals(view, new EndGameCoordinator(scores).begin(result));
    }

    private static StorageException failure(StorageException.Kind kind) {
        return new StorageException(kind, java.nio.file.Path.of("test.json"), null);
    }
}
