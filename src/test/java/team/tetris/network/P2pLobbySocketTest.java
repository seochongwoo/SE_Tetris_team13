package team.tetris.network;

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import team.tetris.application.model.Difficulty;
import team.tetris.application.network.*;
import team.tetris.application.network.MatchMessage.*;
import team.tetris.application.network.LobbyTestSupport.MemoryIps;
import static org.junit.jupiter.api.Assertions.*;

@Timeout(15)
class P2pLobbySocketTest {
    private static P2pLobby lobby(int port, MemoryIps ips) {
        return new P2pLobby(SocketMatchTransport::new, LocalIpv4Addresses::discover,
                new RecentIps(ips), Difficulty.NORMAL, () -> 1234, port);
    }
    private static void await(BooleanSupplier condition) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (!condition.getAsBoolean()) {
            if (System.nanoTime() >= deadline) fail("Timed out waiting for lobby");
            Thread.sleep(5);
        }
    }

    @ParameterizedTest @EnumSource(Mode.class)
    void actualSocketConnectConfigureStartAndRematch(Mode mode) throws Exception {
        var ips = new MemoryIps();
        try (var server = lobby(0, new MemoryIps())) {
            server.host();
            await(() -> server.localPort() > 0);
            try (var client = lobby(server.localPort(), ips)) {
                client.connect("127.0.0.1");
                await(() -> { server.update(); client.update(); return client.configuration().isPresent(); });
                assertEquals(List.of("127.0.0.1"), ips.values);
                server.configure(mode, mode == Mode.TIMED ? 120 : 0);
                await(() -> { client.update(); return client.configuration().orElseThrow().mode() == mode; });
                for (int match = 1; match <= 2; match++) {
                    client.ready();
                    await(() -> { server.update(); return server.remoteReady(); });
                    assertEquals(P2pLobby.Stage.LOBBY, server.stage());
                    server.ready();
                    await(() -> { client.update(); return client.stage() == P2pLobby.Stage.STARTED; });
                    var s = server.takeStartedMatch().orElseThrow();
                    var c = client.takeStartedMatch().orElseThrow();
                    assertEquals(match, s.matchId()); assertEquals(match, c.matchId());
                    assertEquals(s.configuration(), c.configuration());
                    assertEquals(1234, c.pieceSeed());
                    assertTrue(server.takeStartedMatch().isEmpty());
                    s.connection().send(match, new Result(Outcome.DRAW, EndReason.TOP_OUT));
                    await(() -> c.connection().receive().isPresent());
                    client.returnAfterResult(); server.returnAfterResult();
                    await(() -> { client.update(); return client.configuration().isPresent(); });
                    assertFalse(client.localReady()); assertFalse(client.remoteReady());
                    assertFalse(server.localReady()); assertFalse(server.remoteReady());
                }
                client.close();
                await(() -> { server.update(); return server.stage() == P2pLobby.Stage.FAILED; });
            }
        }
    }

    @Test void incompatiblePeerFailsWithoutSavingIp() throws Exception {
        var ips = new MemoryIps();
        try (var server = new SocketMatchTransport(999)) {
            server.listen(0);
            await(() -> server.status().localPort() > 0);
            try (var client = lobby(server.status().localPort(), ips)) {
                client.connect("127.0.0.1");
                await(() -> { client.update(); return client.stage() == P2pLobby.Stage.FAILED; });
                assertTrue(ips.values.isEmpty());
                assertTrue(client.status().contains("접속 실패"));
            }
        }
    }
}
