package team.tetris.storage;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.io.IOException;
import java.util.List;
import team.tetris.application.network.Ipv4Address;
import team.tetris.application.port.RecentIpRepository;
import team.tetris.application.port.StorageException;

/** 버전 헤더와 최신순 IPv4 최대 5개. 읽기 실패 시 원본을 덮어쓰지 않는다. */
public final class FileRecentIpRepository extends AtomicFileRepository<List<String>> implements RecentIpRepository {
    public FileRecentIpRepository(Path path) { super(path); }
    FileRecentIpRepository(Path path, Commit commit) { super(path, commit); }

    @Override public List<String> load() throws StorageException { return read().orElse(List.of()); }
    @Override public void save(List<String> addresses) throws StorageException { write(validate(addresses)); }

    @Override List<String> decode(byte[] contents) throws IOException {
        var lines = StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(contents)).toString().lines().toList();
        if (lines.isEmpty() || !lines.getFirst().equals("tetris-recent-ip-v1"))
            throw new IOException("Invalid recent IP header");
        return validate(lines.subList(1, lines.size()));
    }

    @Override byte[] encode(List<String> addresses) {
        return ("tetris-recent-ip-v1\n" + String.join("\n", validate(addresses)))
                .getBytes(StandardCharsets.UTF_8);
    }

    private static List<String> validate(List<String> addresses) {
        var result = addresses.stream().map(Ipv4Address::normalize).toList();
        if (result.size() > 5 || result.stream().distinct().count() != result.size())
            throw new IllegalArgumentException("Invalid recent IP list");
        return result;
    }
}
