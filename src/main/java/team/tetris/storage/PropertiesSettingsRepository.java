package team.tetris.storage;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.StringWriter;
import java.util.ArrayList;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import team.tetris.application.GameCommand;
import team.tetris.application.model.BattleKeyBindings;
import team.tetris.application.model.Settings;
import team.tetris.application.model.Difficulty;
import team.tetris.application.port.SettingsRepository;
import team.tetris.application.port.StorageException;

/**
 * Java Properties 기반 UTF-8 설정 저장소. 로드 시 원본 변경 금지.
 *
 * <p>형식 3에서 대전 1P·2P 조작 키({@code battle1.*}, {@code battle2.*})를 더했다 (카드 18). 형식 1·2 파일은
 * 그대로 읽고, 대전 키는 기본 배치에서 사용자의 공통 키와 겹치는 키만 대체 키로 바꿔 쓴다. 사용자가 정한 키는
 * 바꾸지 않는다. 파일은 사용자가 설정을 바꿔 저장할 때 형식 3으로 바뀐다.
 */
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
        int version = Integer.parseInt(values.getProperty("schemaVersion"));
        requireVersion(version, 1, 2, 3);
        Set<String> expected = new HashSet<>(Set.of("schemaVersion", "screenSize", "colorBlindMode"));
        if (version >= 2) expected.add("difficulty");
        var battle = new ArrayList<EnumMap<GameCommand, String>>();
        if (version >= 3) {
            for (int player = 1; player <= 2; player++) {
                var keys = new EnumMap<GameCommand, String>(GameCommand.class);
                for (GameCommand command : BattleKeyBindings.COMMANDS) {
                    String field = "battle" + player + "." + command.name();
                    expected.add(field);
                    keys.put(command, values.getProperty(field));
                }
                battle.add(keys);
            }
        }
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
        Settings.ScreenSize size = Settings.ScreenSize.valueOf(values.getProperty("screenSize"));
        Difficulty difficulty = version == 1 ? Difficulty.NORMAL : Difficulty.valueOf(values.getProperty("difficulty"));
        if (version < 3) {
            return new Settings(size, bindings, Boolean.parseBoolean(colorBlind), difficulty);
        }
        return new Settings(size, bindings, Boolean.parseBoolean(colorBlind), difficulty,
                new BattleKeyBindings(battle.get(0), battle.get(1)));
    }

    @Override
    byte[] encode(Settings settings) throws IOException {
        Properties values = new Properties();
        values.setProperty("schemaVersion", "3");
        values.setProperty("difficulty", settings.difficulty().name());
        values.setProperty("screenSize", settings.screenSize().name());
        values.setProperty("colorBlindMode", Boolean.toString(settings.colorBlindMode()));
        settings.keyBindings().forEach((command, key) -> values.setProperty("key." + command.name(), key));
        for (int player = 1; player <= 2; player++) {
            String prefix = "battle" + player + ".";
            settings.battleKeys().of(player).forEach((command, key) -> values.setProperty(prefix + command.name(), key));
        }
        StringWriter writer = new StringWriter();
        values.store(writer, "Tetris settings");
        return writer.toString().getBytes(StandardCharsets.UTF_8);
    }
}
