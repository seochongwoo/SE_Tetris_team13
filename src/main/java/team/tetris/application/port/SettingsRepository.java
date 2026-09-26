package team.tetris.application.port;

import java.util.Optional;
import team.tetris.application.model.Settings;

public interface SettingsRepository {
    /** 파일 부재 시 empty 반환. 읽기 오류는 별도 전달. */
    Optional<Settings> load() throws StorageException;
    /** 저장 실패 시 기존 값 보존. */
    void save(Settings settings) throws StorageException;
}
