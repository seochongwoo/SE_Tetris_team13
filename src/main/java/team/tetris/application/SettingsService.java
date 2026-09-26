package team.tetris.application;

import java.util.Objects;
import java.util.Optional;
import team.tetris.application.model.LoadResult;
import team.tetris.application.model.Settings;
import team.tetris.application.port.SettingsRepository;
import team.tetris.application.port.StorageException;

/** 설정 조회·저장·복원. 저장 성공 이후 UI 적용 필요. */
public final class SettingsService {
    private final SettingsRepository repository;
    private final Settings defaults;

    public SettingsService(SettingsRepository repository) { this(repository, Settings.defaults()); }

    public SettingsService(SettingsRepository repository, Settings defaults) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.defaults = Objects.requireNonNull(defaults, "defaults");
    }

    public Settings get() throws StorageException { return repository.load().orElse(defaults); }

    public LoadResult<Settings> loadOrDefault() {
        try { return new LoadResult<>(get(), Optional.empty()); }
        catch (StorageException error) { return new LoadResult<>(defaults, Optional.of(error)); }
    }

    public void update(Settings settings) throws StorageException {
        repository.save(Objects.requireNonNull(settings, "settings"));
    }

    public void reset() throws StorageException { repository.save(defaults); }
}
