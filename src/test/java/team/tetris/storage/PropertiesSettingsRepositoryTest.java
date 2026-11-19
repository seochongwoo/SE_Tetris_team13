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
import team.tetris.application.GameCommand;
import team.tetris.application.model.BattleKeyBindings;
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
        Files.writeString(file, "schemaVersion=4");
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
            assertEquals("3", properties(Files.readString(file)).getProperty("schemaVersion"));
        }
        Properties legacy = properties(Files.readString(file));
        legacy.setProperty("schemaVersion", "1");
        legacy.remove("difficulty");
        legacy.stringPropertyNames().stream().filter(name -> name.startsWith("battle")).forEach(legacy::remove);
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
        assertEquals("3", properties(Files.readString(file)).getProperty("schemaVersion"));
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

    @Test
    void battleKeysSurviveRestartInFormatThree() throws Exception {
        // 카드 18: 대전 1P·2P 키도 다른 설정처럼 저장하고 다시 읽는다.
        Path file = directory.resolve("settings.properties");
        var repository = new PropertiesSettingsRepository(file);
        Settings changed = Settings.defaults().withBattleKeys(Settings.defaults().battleKeys()
                .with(1, GameCommand.HARD_DROP, "F").with(2, GameCommand.ROTATE_CW, "NUMPAD8"));

        repository.save(changed);

        Properties saved = properties(Files.readString(file));
        assertEquals("3", saved.getProperty("schemaVersion"));
        assertEquals("F", saved.getProperty("battle1.HARD_DROP"));
        assertEquals("NUMPAD8", saved.getProperty("battle2.ROTATE_CW"));
        assertEquals(changed, new PropertiesSettingsRepository(file).load().orElseThrow());
    }

    @Test
    void aVersionTwoFileKeepsEveryUserKeyAndGetsBattleKeysThatAvoidThem() throws Exception {
        // 2차 형식 파일: 일시정지를 W(1P 회전 기본 키)로 바꾼 사용자. 사용자의 키는 그대로 두고 대전 키만 피한다.
        Path file = directory.resolve("settings.properties");
        Properties legacy = new Properties();
        legacy.setProperty("schemaVersion", "2");
        legacy.setProperty("difficulty", "HARD");
        legacy.setProperty("screenSize", "SMALL");
        legacy.setProperty("colorBlindMode", "true");
        Settings.defaults().keyBindings().forEach((command, key) -> legacy.setProperty("key." + command.name(), key));
        legacy.setProperty("key.PAUSE", "W");
        legacy.setProperty("key.RESUME", "W");
        StringWriter writer = new StringWriter();
        legacy.store(writer, null);
        Files.writeString(file, writer.toString());
        byte[] original = Files.readAllBytes(file);
        var repository = new PropertiesSettingsRepository(file);

        Settings loaded = repository.load().orElseThrow();

        assertEquals("W", loaded.keyBindings().get(GameCommand.PAUSE));
        assertEquals(Difficulty.HARD, loaded.difficulty());
        assertEquals("Q", loaded.battleKeys().one().get(GameCommand.ROTATE_CW));
        assertEquals(BattleKeyBindings.standard().two(), loaded.battleKeys().two());
        assertArrayEquals(original, Files.readAllBytes(file), "불러오기만으로 파일을 바꾸지 않는다");
        repository.save(loaded);
        assertEquals("3", properties(Files.readString(file)).getProperty("schemaVersion"));
        assertEquals(loaded, repository.load().orElseThrow());
    }

    @Test
    void rejectsMissingDuplicatedOrClashingBattleKeysAndBattleKeysInOldFormats() throws Exception {
        Path file = directory.resolve("settings.properties");
        var repository = new PropertiesSettingsRepository(file);
        repository.save(Settings.defaults());
        String original = Files.readString(file);
        Properties missing = properties(original);
        missing.remove("battle2.HARD_DROP");
        assertInvalid(repository, file, missing);
        for (String[] change : new String[][] {{"battle2.MOVE_LEFT", "A"}, {"battle1.HARD_DROP", "P"},
                {"battle1.PAUSE", "F"}, {"battle3.ROTATE_CW", "F"}, {"battle1.SOFT_DROP", "s"}}) {
            Properties values = properties(original);
            values.setProperty(change[0], change[1]);
            assertInvalid(repository, file, values);
        }
        Properties oldFormat = properties(original);
        oldFormat.setProperty("schemaVersion", "2");
        assertInvalid(repository, file, oldFormat);
    }

    private static void assertInvalid(PropertiesSettingsRepository repository, Path file, Properties values) throws Exception {
        StringWriter writer = new StringWriter();
        values.store(writer, null);
        Files.writeString(file, writer.toString());
        assertEquals(Kind.INVALID_DATA, assertThrows(StorageException.class, repository::load).kind());
    }
}
