package team.tetris.storage;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import team.tetris.application.*;
import team.tetris.application.model.*;
import team.tetris.application.port.StorageException;
import team.tetris.application.port.StorageException.Kind;

class BinaryScoreRepositoryTest {
    @TempDir Path directory;
    private static GameResult game(long score) {
        return new GameResult(UUID.randomUUID(), score, 0, 0, GameStatus.GAME_OVER);
    }

    @Test
    void roundTripPreservesLargeScoreUnicodeIdsTimestampsAndIdempotency() throws Exception {
        Path file = directory.resolve("nested/scores.bin");
        var repository = new BinaryScoreRepository(file);
        assertEquals(ScoreStore.empty(), repository.load());
        assertFalse(Files.exists(file));
        Instant time = Instant.parse("2026-09-26T12:00:00.123456789Z");
        var service = new ScoreboardService(repository, ScoreboardPolicy.defaults(), Clock.fixed(time, ZoneOffset.UTC));
        var result = game(Long.MAX_VALUE);
        UUID id = service.register(result, " 한글😀 ").orElseThrow();
        var reopened = new ScoreboardService(new BinaryScoreRepository(file));
        ScoreEntry entry = reopened.list().getFirst();
        assertEquals("한글😀", entry.name());
        assertEquals(Long.MAX_VALUE, entry.score());
        assertEquals(time, entry.registeredAt());
        assertEquals(result.gameId(), entry.gameId());
        assertEquals(id, reopened.register(result, "again").orElseThrow());
        assertEquals(1, reopened.list().size());
    }

    @Test
    void evictionReceiptsAndStableTiesSurviveReloadAndResetsStayIndependent() throws Exception {
        Path file = directory.resolve("scores.bin");
        Path settingsFile = directory.resolve("settings.properties");
        var scores = new ScoreboardService(new BinaryScoreRepository(file));
        var settings = new SettingsService(new PropertiesSettingsRepository(settingsFile));
        var changed = new Settings(Settings.ScreenSize.LARGE, Settings.defaults().keyBindings(), true);
        settings.update(changed);
        GameResult first = game(0);
        UUID evictedId = scores.register(first, "evicted").orElseThrow();
        for (int i = 0; i < 10; i++) scores.register(game(10), "name" + i);
        var reopened = new ScoreboardService(new BinaryScoreRepository(file));
        assertEquals(evictedId, reopened.register(first, "again").orElseThrow());
        assertEquals("name0", reopened.list().getFirst().name());
        assertEquals("name9", reopened.list().getLast().name());
        settings.reset();
        assertEquals(10, reopened.list().size());
        settings.update(changed);
        reopened.clear();
        assertEquals(ScoreStore.empty(), new BinaryScoreRepository(file).load());
        assertEquals(changed, new SettingsService(new PropertiesSettingsRepository(settingsFile)).get());
    }

    @Test
    void truncatedInvalidAndTrailingDataAreRejectedWithoutOverwriting() throws Exception {
        Path file = directory.resolve("scores.bin");
        var repository = new BinaryScoreRepository(file);
        new ScoreboardService(repository).register(game(20), "name");
        byte[] original = Files.readAllBytes(file);
        for (int length = 0; length < original.length; length++) {
            assertInvalid(repository, file, Arrays.copyOf(original, length));
        }
        assertInvalid(repository, file, Arrays.copyOf(original, original.length + 1));
        byte[] invalid = original.clone();
        ByteBuffer.wrap(invalid).putInt(0, 0);
        assertInvalid(repository, file, invalid);
        for (int count : new int[]{-1, Integer.MAX_VALUE}) {
            invalid = original.clone();
            ByteBuffer.wrap(invalid).putInt(8, count);
            assertInvalid(repository, file, invalid);
        }
        // 헤더 12바이트, UUID 두 개 32바이트, 이름 길이 2바이트와 name 4바이트 이후 점수 위치.
        invalid = original.clone();
        ByteBuffer.wrap(invalid).putLong(50, -1);
        assertInvalid(repository, file, invalid);
        invalid = original.clone();
        ByteBuffer.wrap(invalid).putInt(66, 1_000_000_000);
        assertInvalid(repository, file, invalid);
        invalid = original.clone();
        invalid[46] = '\n';
        assertInvalid(repository, file, invalid);
        // 등록 이력 없이 종료되는 파일 구성으로 모델 무결성 검증.
        invalid = Arrays.copyOf(original, 74);
        ByteBuffer.wrap(invalid).putInt(70, 0);
        assertInvalid(repository, file, invalid);
        var fallback = new ScoreboardService(repository).loadOrEmpty();
        assertTrue(fallback.value().isEmpty());
        assertEquals(Kind.INVALID_DATA, fallback.error().orElseThrow().kind());
        assertArrayEquals(invalid, Files.readAllBytes(file));
    }

    @Test
    void rejectsUnknownVersionAndDuplicateRegistration() throws Exception {
        Path file = directory.resolve("scores.bin");
        var repository = new BinaryScoreRepository(file);
        new ScoreboardService(repository).register(game(20), "name");
        byte[] original = Files.readAllBytes(file);
        byte[] version = original.clone();
        ByteBuffer.wrap(version).putInt(4, 2);
        Files.write(file, version);
        assertEquals(Kind.UNSUPPORTED_VERSION, assertThrows(StorageException.class, repository::load).kind());
        byte[] duplicate = Arrays.copyOf(original, original.length + 32);
        ByteBuffer.wrap(duplicate).putInt(70, 2);
        System.arraycopy(original, 74, duplicate, original.length, 32);
        assertInvalid(repository, file, duplicate);
    }

    @Test
    void failedCommitDoesNotPublishScoreOrReceiptAndCanRetry() throws Exception {
        Path file = directory.resolve("scores.bin");
        new ScoreboardService(new BinaryScoreRepository(file)).register(game(1), "old");
        byte[] original = Files.readAllBytes(file);
        var failing = new ScoreboardService(new BinaryScoreRepository(file,
                (temporary, target) -> { throw new IOException("replacement failed"); }));
        GameResult result = game(100);
        assertEquals(Kind.WRITE_FAILED,
                assertThrows(StorageException.class, () -> failing.register(result, "new")).kind());
        assertArrayEquals(original, Files.readAllBytes(file));
        var reopened = new ScoreboardService(new BinaryScoreRepository(file));
        assertTrue(reopened.qualifies(result));
        UUID id = reopened.register(result, "new").orElseThrow();
        assertEquals(id, new ScoreboardService(new BinaryScoreRepository(file)).register(result, "retry").orElseThrow());
        assertEquals(2, reopened.list().size());
        try (var files = Files.list(directory)) { assertEquals(1, files.count()); }
    }

    private static void assertInvalid(BinaryScoreRepository repository, Path file, byte[] bytes) throws Exception {
        Files.write(file, bytes);
        assertEquals(Kind.INVALID_DATA, assertThrows(StorageException.class, repository::load).kind());
    }
}
