package team.tetris.application.model;

import java.util.Objects;
import java.util.Optional;
import team.tetris.application.port.StorageException;

/** 임시 실행값과 읽기 오류를 함께 전달. 복구값 자동 저장 금지. */
public record LoadResult<T>(T value, Optional<StorageException> error) {
    public LoadResult {
        Objects.requireNonNull(value, "value");
        Objects.requireNonNull(error, "error");
    }
}
