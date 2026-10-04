package team.tetris.storage.memory;

import java.util.Objects;
import java.util.Optional;
import team.tetris.application.model.Settings;
import team.tetris.application.port.SettingsRepository;

/** 설정 서비스 검증용 메모리 저장소. */
public final class InMemorySettingsRepository implements SettingsRepository {
    private Settings value;
    public Optional<Settings> load() { return Optional.ofNullable(value); }
    public void save(Settings settings) { value = Objects.requireNonNull(settings, "settings"); }
}
