package team.tetris.storage;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import team.tetris.application.network.RecentIps;
import team.tetris.application.network.Ipv4Address;
import team.tetris.application.port.StorageException;
import static org.junit.jupiter.api.Assertions.*;

class FileRecentIpRepositoryTest {
    @TempDir Path directory;
    Path path() { return directory.resolve("recent-ips.txt"); }

    @Test void missingEmptyAndRoundTrip() throws Exception {
        var repo = new FileRecentIpRepository(path());
        assertEquals(List.of(), repo.load());
        assertFalse(Files.exists(path()));
        repo.save(List.of());
        assertEquals(List.of(), repo.load());
        repo.save(List.of("192.168.001.002", "127.0.0.1"));
        assertEquals(List.of("192.168.1.2", "127.0.0.1"), new FileRecentIpRepository(path()).load());
    }

    @Test void newestFiveAreUniqueAndSurviveRestart() {
        var recent = new RecentIps(new FileRecentIpRepository(path()));
        for (int i = 1; i <= 6; i++) recent.connected("10.0.0." + i);
        recent.connected("10.0.0.003");
        assertEquals(List.of("10.0.0.3", "10.0.0.6", "10.0.0.5", "10.0.0.4", "10.0.0.2"), recent.addresses());
        assertEquals(recent.addresses(), new RecentIps(new FileRecentIpRepository(path())).addresses());
    }

    @ParameterizedTest @ValueSource(strings = {"broken", "tetris-recent-ip-v1\n999.1.1.1", "tetris-recent-ip-v1\n10.0.0.1\n10.0.0.1", "tetris-recent-ip-v2"})
    void corruptFileFallsBackWithoutOverwriting(String contents) throws Exception {
        Files.writeString(path(), contents);
        var recent = new RecentIps(new FileRecentIpRepository(path()));
        assertTrue(recent.addresses().isEmpty()); assertFalse(recent.warning().isEmpty());
        assertEquals(contents, Files.readString(path()));
    }

    @Test void invalidUtf8AndTooManyAddressesAreRejected() throws Exception {
        Files.write(path(), new byte[]{(byte) 0xff});
        assertThrows(StorageException.class, () -> new FileRecentIpRepository(path()).load());
        Files.writeString(path(), "tetris-recent-ip-v1\n" + "10.0.0.1\n10.0.0.2\n10.0.0.3\n10.0.0.4\n10.0.0.5\n10.0.0.6");
        assertThrows(StorageException.class, () -> new FileRecentIpRepository(path()).load());
    }

    @Test void atomicCommitFailurePreservesPreviousFileAndAllowsSessionToContinue() throws Exception {
        new FileRecentIpRepository(path()).save(List.of("10.0.0.1"));
        var failing = new FileRecentIpRepository(path(), (temporary, target) -> { throw new IOException("full disk"); });
        var recent = new RecentIps(failing);
        recent.connected("10.0.0.2");
        assertEquals(List.of("10.0.0.2", "10.0.0.1"), recent.addresses());
        assertFalse(recent.warning().isEmpty());
        assertEquals(List.of("10.0.0.1"), new FileRecentIpRepository(path()).load());
        try (var files = Files.list(directory)) { assertEquals(1, files.count()); }
    }

    @ParameterizedTest @ValueSource(strings = {"", "localhost", "1.2.3", "1.2.3.256", "1..2.3", "1.2.3.-1", "0.0.0.0", "224.0.0.1", "255.255.255.255", "1.2.3.4:123"})
    void invalidIpIsRejectedWithoutDns(String input) {
        assertThrows(IllegalArgumentException.class, () -> Ipv4Address.normalize(input));
    }
}
