package team.tetris.network;

import static org.junit.jupiter.api.Assertions.*;
import static team.tetris.application.network.MatchMessage.*;
import static team.tetris.application.network.MatchProtocol.Decision.*;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import team.tetris.application.model.Difficulty;
import team.tetris.application.network.*;
import team.tetris.application.port.MatchTransport;

@Timeout(10)
class SocketMatchTransportTest {
    private static void await(BooleanSupplier condition) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (!condition.getAsBoolean()) {
            if (System.nanoTime() >= deadline) fail("Timed out waiting for transport");
            Thread.sleep(5);
        }
    }

    private static int listen(SocketMatchTransport server) throws InterruptedException {
        server.listen(0);
        await(() -> server.status().localPort() != 0 || server.status().state() == MatchTransport.State.FAILED);
        assertEquals(MatchTransport.State.LISTENING, server.status().state(), server.status().detail());
        return server.status().localPort();
    }

    private static void connect(SocketMatchTransport server, SocketMatchTransport client) throws InterruptedException {
        client.connect("127.0.0.1", listen(server));
        await(() -> server.status().state() == MatchTransport.State.CONNECTED
                && client.status().state() == MatchTransport.State.CONNECTED);
    }

    private static MatchEnvelope receive(SocketMatchTransport transport) throws InterruptedException {
        MatchEnvelope[] result = new MatchEnvelope[1];
        await(() -> { result[0] = transport.poll().orElse(null); return result[0] != null; });
        return result[0];
    }

    @Test
    void localhostDuplexDeliveryIsOrderedAndRttNeverEntersGameQueue() throws Exception {
        try (var server = new SocketMatchTransport(); var client = new SocketMatchTransport()) {
            connect(server, client);
            for (int sequence = 1; sequence <= 20; sequence++) {
                var message = new MatchEnvelope(0, sequence, new Ready(1, sequence % 2 == 0));
                server.send(message); client.send(message);
            }
            for (int sequence = 1; sequence <= 20; sequence++) {
                var expected = new MatchEnvelope(0, sequence, new Ready(1, sequence % 2 == 0));
                assertEquals(expected, receive(client)); assertEquals(expected, receive(server));
            }
            await(() -> server.roundTripNanos().isPresent() && client.roundTripNanos().isPresent());
            assertTrue(server.roundTripNanos().orElseThrow() >= 0);
            assertTrue(server.poll().isEmpty()); assertTrue(client.poll().isEmpty());
            assertThrows(IllegalArgumentException.class, () -> client.send(new MatchEnvelope(0, 1, new Ping(1))));
        }
    }

    @Test
    void handshakeRejectsIncompatibleProtocolsOnBothEnds() throws Exception {
        try (var server = new SocketMatchTransport(); var client = new SocketMatchTransport(PROTOCOL_VERSION + 1)) {
            client.connect("127.0.0.1", listen(server));
            await(() -> server.status().state() == MatchTransport.State.FAILED
                    && client.status().state() == MatchTransport.State.FAILED);
            assertTrue(server.status().detail().contains("Incompatible protocol"));
            assertTrue(client.status().detail().contains("Incompatible protocol"));
            assertTrue(server.poll().isEmpty());
        }
    }

    @Test
    void refusesDataBeforeHelloAndSameRole() throws Exception {
        for (MatchMessage message : List.of(new Ready(1, true), new Hello(PROTOCOL_VERSION, Role.SERVER))) {
            try (var server = new SocketMatchTransport(); var peer = new Socket("127.0.0.1", listen(server))) {
                new MessageCodec().write(new DataOutputStream(peer.getOutputStream()), new MatchEnvelope(0, 1, message));
                await(() -> server.status().state() == MatchTransport.State.FAILED);
                assertTrue(server.poll().isEmpty());
            }
        }
    }

    @Test
    void silentHandshakeTimesOutAndMalformedFrameClosesConnection() throws Exception {
        try (var server = new SocketMatchTransport(); var peer = new Socket("127.0.0.1", listen(server))) {
            await(() -> server.status().state() == MatchTransport.State.FAILED);
            assertFalse(server.status().detail().isBlank());
        }
        try (var server = new SocketMatchTransport(); var peer = new Socket("127.0.0.1", listen(server))) {
            var codec = new MessageCodec();
            var out = new DataOutputStream(peer.getOutputStream());
            codec.write(out, new MatchEnvelope(0, 1, new Hello(PROTOCOL_VERSION, Role.CLIENT)));
            codec.read(new DataInputStream(peer.getInputStream()));
            await(() -> server.status().state() == MatchTransport.State.CONNECTED);
            out.writeInt(Integer.MAX_VALUE); out.flush();
            await(() -> server.status().state() == MatchTransport.State.FAILED);
            assertTrue(server.status().detail().contains("frame length"));
        }
    }

    @Test
    void receiveQueueOverflowFailsInsteadOfSilentlyLosingAttacks() throws Exception {
        try (var server = new SocketMatchTransport(); var peer = new Socket("127.0.0.1", listen(server))) {
            var codec = new MessageCodec();
            var out = new DataOutputStream(peer.getOutputStream());
            codec.write(out, new MatchEnvelope(0, 1, new Hello(PROTOCOL_VERSION, Role.CLIENT)));
            codec.read(new DataInputStream(peer.getInputStream()));
            await(() -> server.status().state() == MatchTransport.State.CONNECTED);
            for (int sequence = 1; sequence <= 257; sequence++) {
                codec.write(out, new MatchEnvelope(1, sequence, new Attack(sequence, List.of(3, 5))));
            }
            await(() -> server.status().state() == MatchTransport.State.FAILED);
            assertEquals("Receive queue overflow", server.status().detail());
            assertTrue(server.poll().isEmpty());
        }
    }

    @Test
    void closeCancelsAcceptAndAllowsRebindingAndDisconnectedPeerFails() throws Exception {
        int port;
        try (var server = new SocketMatchTransport()) {
            port = listen(server); server.close(); server.close();
            assertEquals(MatchTransport.State.CLOSED, server.status().state());
            assertThrows(IllegalStateException.class, () -> server.listen(0));
        }
        try (var server = new SocketMatchTransport(); var client = new SocketMatchTransport()) {
            server.listen(port);
            await(() -> server.status().localPort() == port);
            client.connect("127.0.0.1", port);
            await(() -> client.status().state() == MatchTransport.State.CONNECTED
                    && server.status().state() == MatchTransport.State.CONNECTED);
            client.close();
            await(() -> server.status().state() == MatchTransport.State.FAILED);
            assertThrows(IllegalStateException.class, () -> client.send(new MatchEnvelope(0, 1, new Leave())));
        }
        try (var server = new SocketMatchTransport(); var client = new SocketMatchTransport()) {
            connect(server, client);
        }
    }

    @Test
    void connectionFailuresAndInvalidLifecycleAreObservable() throws Exception {
        try (var transport = new SocketMatchTransport()) {
            assertThrows(IllegalArgumentException.class, () -> transport.listen(-1));
            assertThrows(IllegalArgumentException.class, () -> transport.connect(" ", 1000));
            assertThrows(IllegalArgumentException.class, () -> transport.connect("127.0.0.1", 0));
            assertThrows(IllegalStateException.class, () -> transport.send(new MatchEnvelope(0, 1, new Leave())));
        }
        try (var occupied = new ServerSocket(0); var server = new SocketMatchTransport()) {
            server.listen(occupied.getLocalPort());
            await(() -> server.status().state() == MatchTransport.State.FAILED);
        }
        int closedPort;
        try (var reservation = new ServerSocket(0)) { closedPort = reservation.getLocalPort(); }
        try (var client = new SocketMatchTransport()) {
            client.connect("127.0.0.1", closedPort);
            await(() -> client.status().state() == MatchTransport.State.FAILED);
            assertFalse(client.status().detail().isBlank());
        }
    }

    @Test
    void socketReceptionDoesNotAdvanceProtocolUntilGameThreadDrainsAndValidates() throws Exception {
        var protocol = new MatchProtocol();
        try (var server = new SocketMatchTransport(); var client = new SocketMatchTransport()) {
            connect(server, client);
            var lobby = new MatchEnvelope(0, 1, new Lobby(1, Mode.NORMAL, Difficulty.NORMAL, 0));
            server.send(lobby);
            assertEquals(ACCEPTED, protocol.accept(Role.SERVER, receive(client)));
            assertEquals(ACCEPTED, protocol.accept(Role.SERVER, new MatchEnvelope(0, 2, new Ready(1, true))));
            assertEquals(ACCEPTED, protocol.accept(Role.CLIENT, new MatchEnvelope(0, 1, new Ready(1, true))));
            var start = new MatchEnvelope(1, 3, new Start(1, 99));
            server.send(start);
            var queued = receive(client);
            assertEquals(MatchProtocol.Phase.LOBBY, protocol.phase());
            assertEquals(ACCEPTED, protocol.accept(Role.SERVER, queued));
            assertEquals(MatchProtocol.Phase.PLAYING, protocol.phase());

            var attack = new Attack(1, List.of(3, 5));
            server.send(new MatchEnvelope(1, 4, attack));
            server.send(new MatchEnvelope(1, 5, attack));
            server.send(new MatchEnvelope(99, 6, new Attack(2, attack.rows())));
            assertEquals(ACCEPTED, protocol.accept(Role.SERVER, receive(client)));
            assertEquals(DUPLICATE, protocol.accept(Role.SERVER, receive(client)));
            assertEquals(STALE_MATCH, protocol.accept(Role.SERVER, receive(client)));
        }
    }

    @Test
    void onlyServerCanPublishAuthoritativeTimeAndFinalResultBlocksFurtherState() {
        var protocol = new MatchProtocol();
        assertEquals(ACCEPTED, protocol.accept(Role.SERVER,
                new MatchEnvelope(0, 1, new Lobby(1, Mode.TIMED, Difficulty.NORMAL, 120))));
        assertEquals(ACCEPTED, protocol.accept(Role.SERVER, new MatchEnvelope(0, 2, new Ready(1, true))));
        assertEquals(ACCEPTED, protocol.accept(Role.CLIENT, new MatchEnvelope(0, 1, new Ready(1, true))));
        assertEquals(ACCEPTED, protocol.accept(Role.SERVER, new MatchEnvelope(1, 3, new Start(1, 7))));
        var player = MessageCodecTest.player();
        assertEquals(WRONG_SENDER, protocol.accept(Role.CLIENT, new MatchEnvelope(1, 2, new State(player, 120))));
        assertEquals(WRONG_SENDER, protocol.accept(Role.SERVER, new MatchEnvelope(1, 4, new State(player, -1))));
        assertEquals(ACCEPTED, protocol.accept(Role.CLIENT, new MatchEnvelope(1, 2, new State(player, -1))));
        assertEquals(ACCEPTED, protocol.accept(Role.SERVER, new MatchEnvelope(1, 4, new State(player, 120))));
        assertEquals(ACCEPTED, protocol.accept(Role.SERVER,
                new MatchEnvelope(1, 5, new Result(Outcome.DRAW, EndReason.TIME_LIMIT))));
        assertEquals(WRONG_STATE, protocol.accept(Role.CLIENT, new MatchEnvelope(1, 3, new State(player, -1))));
    }
}
