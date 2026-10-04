package team.tetris.application.port;

import java.io.IOException;
import java.nio.file.Path;

/** UI에서 식별 가능한 저장 오류 종류와 대상 경로 전달. */
public final class StorageException extends IOException {
    private static final long serialVersionUID = 1L;
    public enum Kind { READ_FAILED, INVALID_DATA, UNSUPPORTED_VERSION, WRITE_FAILED }
    private final Kind kind;
    private final Path path;

    public StorageException(Kind kind, Path path, Throwable cause) {
        super(kind + ": " + path, cause);
        this.kind = kind;
        this.path = path;
    }

    public Kind kind() { return kind; }
    public Path path() { return path; }
}
