package team.tetris.network;

import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.stream.Stream;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import team.tetris.application.GameCommand;
import team.tetris.application.model.Difficulty;
import team.tetris.application.network.*;
import team.tetris.application.network.LobbyTestSupport.MemoryIps;
import team.tetris.application.network.MatchMessage.*;
import team.tetris.application.port.MatchTransport;
import team.tetris.bootstrap.AppComposition;
import static org.junit.jupiter.api.Assertions.*;

/** 실제 EOF가 application까지 전달되고 새 연결에서 다시 경기할 수 있는지 검증한다. */
@Timeout(20)
class P2pRecoverySocketTest {
    @TempDir Path directory;
    enum Phase { LOBBY, PLAYING }

    static Stream<Arguments> phasesAndRoles() {
        return Stream.of(Phase.values()).flatMap(phase ->
                Stream.of(Role.values()).map(role -> Arguments.of(phase, role)));
    }

    private static void await(BooleanSupplier condition) throws InterruptedException {
        long deadline = System.nanoTime() + 5_000_000_000L;
        while (!condition.getAsBoolean()) {
            if (System.nanoTime() >= deadline) fail("Timed out waiting for P2P recovery");
            Thread.sleep(5);
        }
    }

    private static P2pLobby lobby(SocketMatchTransport transport, int port, MemoryIps ips) {
        return new P2pLobby(() -> transport, List::of, new RecentIps(ips),
                Difficulty.NORMAL, () -> 42, port);
    }

    @ParameterizedTest @EnumSource(Role.class)
    void peerDisconnectDuringHandshakeFailsWithoutSavingIpAndCanReconnect(Role role) throws Exception {
        int port;
        var ips = new MemoryIps();
        try (var wire = new SocketMatchTransport()) {
            if (role == Role.SERVER) {
                try (var endpoint = lobby(wire, 0, ips)) {
                    endpoint.host();
                    await(() -> endpoint.localPort() > 0);
                    port = endpoint.localPort();
                    try (var peer = new Socket("127.0.0.1", port)) {
                        await(() -> wire.status().state() == MatchTransport.State.HANDSHAKING);
                    } // HELLO 이전 EOF
                    assertHandshakeFailure(endpoint, wire, ips);
                }
            } else {
                try (var listener = new ServerSocket(0)) {
                    listener.setSoTimeout(5_000);
                    port = listener.getLocalPort();
                    try (var endpoint = lobby(wire, port, ips)) {
                        endpoint.connect("127.0.0.1");
                        try (var peer = listener.accept()) {
                            await(() -> wire.status().state() == MatchTransport.State.HANDSHAKING);
                        }
                        assertHandshakeFailure(endpoint, wire, ips);
                    }
                }
            }
        }
        assertReconnectAndPlay(port);
    }

    private static void assertHandshakeFailure(P2pLobby endpoint, SocketMatchTransport wire, MemoryIps ips)
            throws InterruptedException {
        await(() -> { endpoint.update(); return endpoint.stage() == P2pLobby.Stage.FAILED; });
        assertTrue(endpoint.status().contains("접속 실패"));
        assertEquals(0, ips.writes);
        assertTrue(endpoint.configuration().isEmpty());
        assertTrue(endpoint.takeStartedMatch().isEmpty());
        await(wire::workersTerminated);
        assertTrue(wire.poll().isEmpty());
    }

    @ParameterizedTest @MethodSource("phasesAndRoles")
    void peerDisconnectInLobbyOrGameStopsSessionAndReconnectsOnSamePort(Phase phase, Role survivor)
            throws Exception {
        int port;
        try (var pair = new Pair(0)) {
            pair.connect();
            port = pair.server.localPort();
            var endpoint = survivor == Role.SERVER ? pair.server : pair.client;
            var wire = survivor == Role.SERVER ? pair.sw : pair.cw;
            var peer = survivor == Role.SERVER ? pair.cw : pair.sw;
            if (phase == Phase.LOBBY) {
                endpoint.ready(); // 끊기기 전에 보낸 준비 상태도 재접속에 남지 않아야 한다.
                peer.close();
                await(() -> { endpoint.update(); return endpoint.stage() == P2pLobby.Stage.FAILED; });
                assertFalse(endpoint.localReady());
                assertFalse(endpoint.remoteReady());
                assertTrue(endpoint.takeStartedMatch().isEmpty());
            } else {
                pair.start();
                var match = survivor == Role.SERVER ? pair.s : pair.c;
                match.handle(GameCommand.SOFT_DROP);
                var frozen = match.local();
                long remaining = match.remainingNanos();
                peer.close();
                await(() -> { match.update(0); return match.result().isPresent(); });
                match.handle(GameCommand.HARD_DROP);
                match.update(10_000_000_000L);
                assertEquals(frozen, match.local());
                assertEquals(remaining, match.remainingNanos());
                assertTrue(match.failure().isEmpty());
                var result = match.result().orElseThrow();
                assertEquals(EndReason.LEFT, result.reason());
                assertEquals(survivor == Role.SERVER ? Outcome.SERVER_WINS : Outcome.CLIENT_WINS, result.outcome());
            }
            await(() -> pair.sw.workersTerminated() && pair.cw.workersTerminated());
            assertTrue(wire.poll().isEmpty());
            assertFalse(wire.lagging());
        }
        assertReconnectAndPlay(port);
        assertFalse(Files.exists(directory.resolve("server/scores.bin")));
        assertFalse(Files.exists(directory.resolve("client/scores.bin")));
    }

    private void assertReconnectAndPlay(int port) throws Exception {
        try (var pair = new Pair(port)) {
            pair.connect();
            assertEquals(port, pair.server.localPort());
            assertFalse(pair.server.localReady()); assertFalse(pair.server.remoteReady());
            assertFalse(pair.client.localReady()); assertFalse(pair.client.remoteReady());
            pair.start();
            assertEquals(1, pair.s.matchId()); assertEquals(1, pair.c.matchId());
            assertEquals(0, pair.s.local().score()); assertEquals(0, pair.c.local().score());
            assertTrue(pair.s.local().pendingRows().isEmpty());
            assertTrue(pair.c.local().pendingRows().isEmpty());
            assertEquals(60_000_000_000L, pair.s.remainingNanos());
            pair.c.handle(GameCommand.MOVE_LEFT);
            await(() -> { pair.s.update(0); return pair.c.local().equals(pair.s.remote().orElseThrow()); });
            pair.s.handle(GameCommand.QUIT_GAME);
            await(() -> { pair.c.update(0); return pair.c.result().isPresent(); });
            assertEquals(pair.s.result(), pair.c.result());
        }
    }

    private final class Pair implements AutoCloseable {
        final SocketMatchTransport sw = new SocketMatchTransport(), cw = new SocketMatchTransport();
        final P2pLobby server;
        P2pLobby client;
        P2pMatch s, c;

        Pair(int port) { server = lobby(sw, port, new MemoryIps()); }

        void connect() throws InterruptedException {
            server.host();
            await(() -> server.localPort() > 0);
            client = lobby(cw, server.localPort(), new MemoryIps());
            client.connect("127.0.0.1");
            await(() -> { server.update(); client.update(); return client.configuration().isPresent(); });
        }

        void start() throws InterruptedException {
            server.configure(Mode.TIMED, 60);
            await(() -> { client.update(); return client.configuration().orElseThrow().mode() == Mode.TIMED; });
            server.ready(); client.ready();
            await(() -> { server.update(); client.update(); return client.stage() == P2pLobby.Stage.STARTED; });
            s = new AppComposition(directory.resolve("server")).newP2pMatch(server.takeStartedMatch().orElseThrow());
            c = new AppComposition(directory.resolve("client")).newP2pMatch(client.takeStartedMatch().orElseThrow());
            await(() -> { s.update(0); c.update(0); return s.remote().isPresent() && c.remote().isPresent(); });
        }

        @Override public void close() {
            server.close();
            if (client != null) client.close();
            sw.close(); cw.close();
        }
    }
}
