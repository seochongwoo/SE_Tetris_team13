package team.tetris.storage;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ConsoleFileDebugLogTest {
    @TempDir Path directory;

    @Test void appendsUtf8EntriesWithoutTruncatingPreviousRuns() throws Exception {
        Path path = directory.resolve("logs/debug.log");
        new ConsoleFileDebugLog(path).append("상태 1\n");
        var reopened = new ConsoleFileDebugLog(path);
        reopened.append("상태 2\n");
        assertEquals("상태 1\n상태 2\n", Files.readString(path));
        assertTrue(reopened.description().contains(path.toString()));
    }

    @Test void fileFailureIsVisibleWithoutInterruptingTheGame() throws Exception {
        Path blocked = directory.resolve("file");
        Files.writeString(blocked, "not a directory");
        var log = new ConsoleFileDebugLog(blocked.resolve("debug.log"));
        assertDoesNotThrow(() -> log.append("state\n"));
        assertTrue(log.description().contains("저장 실패"));
    }
}
