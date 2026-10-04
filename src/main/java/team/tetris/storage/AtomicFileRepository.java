package team.tetris.storage;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.Objects;
import java.util.Optional;
import team.tetris.application.port.StorageException;
import team.tetris.application.port.StorageException.Kind;

/** 파일 읽기와 같은 디렉터리의 임시 파일을 이용한 원자적 교체. */
abstract class AtomicFileRepository<T> {
    private final Path path;
    private final Commit commit;

    @FunctionalInterface
    interface Commit { void replace(Path temporary, Path target) throws IOException; }

    AtomicFileRepository(Path path) {
        this(path, (temporary, target) -> Files.move(temporary, target,
                StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING));
    }

    AtomicFileRepository(Path path, Commit commit) {
        this.path = Objects.requireNonNull(path, "path").toAbsolutePath().normalize();
        this.commit = Objects.requireNonNull(commit, "commit");
    }

    final Optional<T> read() throws StorageException {
        byte[] contents;
        try { contents = Files.readAllBytes(path); }
        catch (NoSuchFileException missing) { return Optional.empty(); }
        catch (IOException error) { throw new StorageException(Kind.READ_FAILED, path, error); }
        try {
            return Optional.of(decode(contents));
        } catch (StorageException error) {
            throw error;
        } catch (IOException | RuntimeException invalid) {
            throw new StorageException(Kind.INVALID_DATA, path, invalid);
        }
    }

    final void write(T value) throws StorageException {
        Objects.requireNonNull(value, "value");
        Path temporary = null;
        try {
            byte[] bytes = encode(value);
            Files.createDirectories(path.getParent());
            temporary = Files.createTempFile(path.getParent(), ".tetris-", ".tmp");
            try (FileChannel channel = FileChannel.open(temporary, StandardOpenOption.WRITE)) {
                ByteBuffer buffer = ByteBuffer.wrap(bytes);
                while (buffer.hasRemaining()) channel.write(buffer);
                channel.force(true);
            }
            commit.replace(temporary, path);
        } catch (IOException error) {
            throw new StorageException(Kind.WRITE_FAILED, path, error);
        } finally {
            if (temporary != null) {
                try { Files.deleteIfExists(temporary); }
                catch (IOException ignored) { /* 저장 오류를 가리지 않도록 임시 파일 정리 실패 무시. */ }
            }
        }
    }

    final void requireVersion(int version) throws StorageException {
        if (version != 1) throw new StorageException(Kind.UNSUPPORTED_VERSION, path, null);
    }

    abstract T decode(byte[] contents) throws IOException;
    abstract byte[] encode(T value) throws IOException;
}
