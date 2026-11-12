package team.tetris.application.port;

import java.util.List;

public interface RecentIpRepository {
    List<String> load() throws StorageException;
    void save(List<String> addresses) throws StorageException;
}
