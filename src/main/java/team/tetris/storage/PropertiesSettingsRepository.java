package team.tetris.storage;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import team.tetris.application.GameCommand;
import team.tetris.application.model.Settings;
import team.tetris.application.port.SettingsRepository;
import team.tetris.application.port.StorageException;

/** Java Properties 기반 UTF-8 설정 저장소. 로드 시 원본 변경 금지. */
public final class PropertiesSettingsRepository extends AtomicFileRepository<Settings> implements SettingsRepository {
    public PropertiesSettingsRepository(Path path) { super(path); }
    PropertiesSettingsRepository(Path path, Commit commit) { super(path, commit); }

    @Override
    public Optional<Settings> load() throws StorageException { return read(); }
    @Override
    public void save(Settings settings) throws StorageException { write(settings); }

    @Override
    Settings decode(byte[] contents) throws IOException {
        Properties values = new Properties() {
            @Override
            public synchronized Object put(Object key, Object value) {
                if (containsKey(key)) throw new IllegalArgumentException("Duplicate setting: " + key);
                return super.put(key, value);
            }
        };
        try (var reader = new InputStreamReader(new ByteArrayInputStream(contents), StandardCharsets.UTF_8.newDecoder())) {
            values.load(reader);
        }
        requireVersion(Integer.parseInt(values.getProperty("schemaVersion")));
        Set<String> expected = new HashSet<>(Set.of("schemaVersion", "screenSize", "colorBlindMode"));
        var bindings = new EnumMap<GameCommand, String>(GameCommand.class);
        for (GameCommand command : GameCommand.values()) {
            String field = "key." + command.name();
            expected.add(field);
            bindings.put(command, values.getProperty(field));
        }
        if (!values.stringPropertyNames().equals(expected)) throw new IllegalArgumentException("Unexpected settings");
        String colorBlind = values.getProperty("colorBlindMode");
        if (!"true".equals(colorBlind) && !"false".equals(colorBlind)) {
            throw new IllegalArgumentException("Expected true or false");
        }
        return new Settings(Settings.ScreenSize.valueOf(values.getProperty("screenSize")), bindings,
                Boolean.parseBoolean(colorBlind));
    }

    @Override
    byte[] encode(Settings settings) throws IOException {
        Properties values = new Properties();
        values.setProperty("schemaVersion", "1");
        values.setProperty("screenSize", settings.screenSize().name());
        values.setProperty("colorBlindMode", Boolean.toString(settings.colorBlindMode()));
        settings.keyBindings().forEach((command, key) -> values.setProperty("key." + command.name(), key));
        StringWriter writer = new StringWriter();
        values.store(writer, "Tetris settings");
        return writer.toString().getBytes(StandardCharsets.UTF_8);
    }
}
