package team.tetris.storage;

import static org.junit.jupiter.api.Assertions.*;
import java.util.Properties;
import java.io.StringReader;
import java.io.StringWriter;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import team.tetris.application.SettingsService;
import team.tetris.application.model.Settings;
import team.tetris.application.model.Difficulty;
import team.tetris.application.port.StorageException;
import team.tetris.application.port.StorageException.Kind;

class PropertiesSettingsRepositoryTest {
    @TempDir Path directory;

    @Test
    void missingFileDoesNotCreateDirectoriesAndRoundTripRestoresAllFields() throws Exception {
        Path file = directory.resolve("nested/settings.properties");
        var repository = new PropertiesSettingsRepository(file);
        assertTrue(repository.load().isEmpty());
        assertFalse(Files.exists(file.getParent()));
        for (Settings.ScreenSize size : Settings.ScreenSize.values()) {
            Settings settings = new Settings(size, Settings.defaults().keyBindings(), true);
            repository.save(settings);
            assertEquals(settings, new PropertiesSettingsRepository(file).load().orElseThrow());
        }
        new SettingsService(repository).reset();
        assertEquals(Settings.defaults(), new PropertiesSettingsRepository(file).load().orElseThrow());
        try (var files = Files.list(file.getParent())) { assertEquals(1, files.count()); }
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "null", "{}", "schemaVersion=no", "schemaVersion=1.5",
            "schemaVersion=2147483648", "schemaVersion=1\nschemaVersion=1", "schemaVersion=1\nbad=\\uXXXX"})
    void malformedSettingsAreReportedAndNeverOverwrittenByFallback(String contents) throws Exception {
        Path file = directory.resolve("settings.properties");
        Files.writeString(file, contents);
        var repository = new PropertiesSettingsRepository(file);
        assertEquals(Kind.INVALID_DATA, assertThrows(StorageException.class, repository::load).kind());
        var loaded = new SettingsService(repository).loadOrDefault();
        assertEquals(Settings.defaults(), loaded.value());
        assertTrue(loaded.error().isPresent());
        assertEquals(contents, Files.readString(file));
    }

    @Test
    void rejectsMissingFieldsWrongValuesUnknownCommandsAndConflictingBindings() throws Exception {
        Path file = directory.resolve("settings.properties");
        var repository = new PropertiesSettingsRepository(file);
        repository.save(Settings.defaults());
        String original = Files.readString(file);
        for (String field : List.of("screenSize", "colorBlindMode", "key.MOVE_LEFT", "difficulty")) {
            Properties values = properties(original);
            values.remove(field);
            assertInvalid(repository, file, values);
        }
        for (String[] change : new String[][] {{"difficulty", "UNKNOWN"}, {"colorBlindMode", "FALSE"}, {"screenSize", "HUGE"},
                {"key.UNKNOWN", "A"}, {"key.MOVE_LEFT", "RIGHT"}}) {
            Properties values = properties(original);
            values.setProperty(change[0], change[1]);
            assertInvalid(repository, file, values);
        }
        Files.write(file, new byte[]{(byte) 0xc3, 0x28});
        assertEquals(Kind.INVALID_DATA, assertThrows(StorageException.class, repository::load).kind());
    }

    private static Properties properties(String text) throws Exception {
        Properties values = new Properties();
        values.load(new StringReader(text));
        return values;
    }

    @Test
    void reportsUnknownVersionAndReadIoFailureSeparately() throws Exception {
        Path file = directory.resolve("settings.properties");
        Files.writeString(file, "schemaVersion=3");
        var exception = assertThrows(StorageException.class, () -> new PropertiesSettingsRepository(file).load());
        assertEquals(Kind.UNSUPPORTED_VERSION, exception.kind());
        assertEquals(file.toAbsolutePath(), exception.path());
        assertEquals(Kind.READ_FAILED,
                assertThrows(StorageException.class, () -> new PropertiesSettingsRepository(directory).load()).kind());
    }

    @Test
    void failedAtomicReplacementPreservesExistingBytesAndCleansTemporaryFile() throws Exception {
        Path file = directory.resolve("settings.properties");
        new PropertiesSettingsRepository(file).save(Settings.defaults());
        byte[] original = Files.readAllBytes(file);
        for (IOException failure : List.of(new IOException("disk error"),
                new AtomicMoveNotSupportedException("source", "target", "unsupported"))) {
            var failing = new PropertiesSettingsRepository(file, (temporary, target) -> {
                assertTrue(Files.size(temporary) > 0);
                throw failure;
            });
            var changed = new Settings(Settings.ScreenSize.LARGE, Settings.defaults().keyBindings(), true);
            assertEquals(Kind.WRITE_FAILED, assertThrows(StorageException.class, () -> failing.save(changed)).kind());
            assertArrayEquals(original, Files.readAllBytes(file));
            try (var files = Files.list(directory)) { assertEquals(1, files.count()); }
        }
    }

    @Test
    void allDifficultiesSurviveRestartAndV1LoadsAsNormalWithoutRewriting() throws Exception {
        Path file = directory.resolve("settings.properties");
        var repository = new PropertiesSettingsRepository(file);
        for (Difficulty difficulty : Difficulty.values()) {
            var settings = new Settings(Settings.ScreenSize.LARGE, Settings.defaults().keyBindings(), true, difficulty);
            repository.save(settings);
            assertEquals(settings, new PropertiesSettingsRepository(file).load().orElseThrow());
            assertEquals("2", properties(Files.readString(file)).getProperty("schemaVersion"));
        }
        Properties legacy = properties(Files.readString(file));
        legacy.setProperty("schemaVersion", "1");
        legacy.remove("difficulty");
        StringWriter writer = new StringWriter();
        legacy.store(writer, null);
        Files.writeString(file, writer.toString());
        byte[] original = Files.readAllBytes(file);
        Settings loaded = repository.load().orElseThrow();
        assertEquals(Difficulty.NORMAL, loaded.difficulty());
        assertEquals(Settings.ScreenSize.LARGE, loaded.screenSize());
        assertTrue(loaded.colorBlindMode());
        assertArrayEquals(original, Files.readAllBytes(file));
        repository.save(loaded);
        assertEquals("2", properties(Files.readString(file)).getProperty("schemaVersion"));
        assertEquals(loaded, repository.load().orElseThrow());
    }

    @Test
    void invalidParentPathFailsWithoutDestroyingExistingFile() throws Exception {
        Path parent = directory.resolve("not-a-directory");
        Files.writeString(parent, "keep");
        var repository = new PropertiesSettingsRepository(parent.resolve("settings.properties"));
        assertEquals(Kind.WRITE_FAILED,
                assertThrows(StorageException.class, () -> repository.save(Settings.defaults())).kind());
        assertEquals("keep", Files.readString(parent));
    }

    private static void assertInvalid(PropertiesSettingsRepository repository, Path file, Properties values) throws Exception {
        StringWriter writer = new StringWriter();
        values.store(writer, null);
        Files.writeString(file, writer.toString());
        assertEquals(Kind.INVALID_DATA, assertThrows(StorageException.class, repository::load).kind());
    }
}
