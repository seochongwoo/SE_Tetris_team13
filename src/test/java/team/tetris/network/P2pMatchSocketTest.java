package team.tetris.network;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import team.tetris.application.GameCommand;
import team.tetris.application.model.Difficulty;
import team.tetris.application.network.*;
import team.tetris.application.network.MatchMessage.*;
import team.tetris.bootstrap.AppComposition;
import static org.junit.jupiter.api.Assertions.*;

@Timeout(20)
class P2pMatchSocketTest {
    @TempDir Path directory;
    private static P2pLobby lobby(int port) {
        return new P2pLobby(SocketMatchTransport::new, List::of,
                new RecentIps(new LobbyTestSupport.MemoryIps()), Difficulty.HARD, () -> 321, port);
    }
    private static void await(BooleanSupplier condition) throws InterruptedException {
        long deadline = System.nanoTime() + 5_000_000_000L;
        while (!condition.getAsBoolean()) {
            if (System.nanoTime() > deadline) fail("Timed out");
            Thread.sleep(5);
        }
    }
    @ParameterizedTest @EnumSource(Mode.class)
    void realCompositionTwoGamesStatePauseResultAndNoScoreFile(Mode mode) throws Exception {
        var serverApp = new AppComposition(directory.resolve("server"));
        var clientApp = new AppComposition(directory.resolve("client"));
        try (var server = lobby(0)) {
            server.host(); await(() -> server.localPort() > 0);
            try (var client = lobby(server.localPort())) {
                client.connect("127.0.0.1");
                await(() -> { server.update(); client.update(); return client.configuration().isPresent(); });
                server.configure(mode, mode == Mode.TIMED ? 60 : 0);
                await(() -> { client.update(); return client.configuration().orElseThrow().mode() == mode; });
                for (int round = 1; round <= 2; round++) {
                    server.ready(); client.ready();
                    await(() -> { server.update(); client.update(); return client.stage() == P2pLobby.Stage.STARTED; });
                    var s = serverApp.newP2pMatch(server.takeStartedMatch().orElseThrow());
                    var c = clientApp.newP2pMatch(client.takeStartedMatch().orElseThrow());
                    assertEquals(round, s.matchId());
                    await(() -> { s.update(0); c.update(0); return s.remote().isPresent() && c.remote().isPresent(); });
                    assertEquals(s.local(), c.local());
                    s.handle(GameCommand.MOVE_LEFT);
                    await(() -> { c.update(0); return s.local().equals(c.remote().orElseThrow()); });
                    c.handle(GameCommand.HARD_DROP);
                    await(() -> { s.update(0); return c.local().equals(s.remote().orElseThrow()); });
                    c.handle(GameCommand.PAUSE);
                    await(() -> { s.update(0); c.update(0); return s.paused() && c.paused(); });
                    var before = s.local();
                    s.update(10_000_000_000L); assertEquals(before, s.local());
                    c.handle(GameCommand.RESUME);
                    await(() -> { s.update(0); c.update(0); return !s.paused() && !c.paused(); });
                    if (mode == Mode.TIMED) s.update(60_000_000_000L);
                    else s.handle(GameCommand.QUIT_GAME);
                    await(() -> { s.update(0); c.update(0); return c.result().isPresent(); });
                    assertEquals(s.result(), c.result());
                    assertEquals(s.local(), c.remote().orElseThrow());
                    assertEquals(c.local(), s.remote().orElseThrow());
                    server.returnAfterResult(); client.returnAfterResult();
                    await(() -> { client.update(); return client.configuration().isPresent(); });
                    assertFalse(server.localReady()); assertFalse(client.localReady());
                }
            }
        }
        assertFalse(Files.exists(directory.resolve("server/scores.bin")));
        assertFalse(Files.exists(directory.resolve("client/scores.bin")));
    }
}
