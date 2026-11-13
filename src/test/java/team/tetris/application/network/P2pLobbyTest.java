package team.tetris.application.network;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import team.tetris.application.model.Difficulty;
import team.tetris.application.network.MatchMessage.*;
import team.tetris.application.network.LobbyTestSupport.*;
import team.tetris.application.port.MatchTransport;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class P2pLobbyTest {
    private final Transport serverWire = new Transport();
    private final Transport clientWire = new Transport();
    private final MemoryIps ips = new MemoryIps();
    private final P2pLobby server = lobby(serverWire, new MemoryIps());
    private final P2pLobby client = lobby(clientWire, ips);

    private static P2pLobby lobby(Transport transport, MemoryIps ips) {
        return new P2pLobby(() -> transport, () -> List.of("192.168.1.2", "10.0.0.2"),
                new RecentIps(ips), Difficulty.HARD, () -> 42);
    }
    private void connect() {
        serverWire.peer = clientWire;
        clientWire.peer = serverWire;
        server.host();
        client.connect("192.168.001.002");
        serverWire.connected(); clientWire.connected();
        server.update(); client.update();
    }

    @Test void connectionStagesAndOnlySuccessfulClientConnectionIsRemembered() {
        server.host();
        assertEquals(List.of("192.168.1.2", "10.0.0.2"), server.addresses());
        assertEquals("접속 대기 중", server.status());
        client.connect("192.168.001.002");
        assertEquals("192.168.1.2", clientWire.host);
        assertEquals(0, ips.writes);
        clientWire.status = new MatchTransport.Status(MatchTransport.State.HANDSHAKING, "", 1);
        client.update();
        assertEquals("연결 확인 중", client.status());
        clientWire.connected(); client.update(); client.update();
        assertEquals(1, ips.writes);
        assertEquals(List.of("192.168.1.2"), ips.values);
        assertEquals(P2pLobby.Stage.LOBBY, client.stage());
        client.ready();
        assertFalse(client.localReady()); // 서버 설정 수신 전
    }

    @ParameterizedTest @EnumSource(Role.class)
    void oneReadyPlayerNeverStartsAndBothStartExactlyOnce(Role first) {
        connect();
        var firstLobby = first == Role.SERVER ? server : client;
        var secondLobby = first == Role.SERVER ? client : server;
        firstLobby.ready();
        server.update(); client.update();
        assertEquals(P2pLobby.Stage.LOBBY, server.stage());
        assertEquals(P2pLobby.Stage.LOBBY, client.stage());
        secondLobby.ready(); server.update(); client.update();
        var serverStart = server.takeStartedMatch().orElseThrow();
        var clientStart = client.takeStartedMatch().orElseThrow();
        assertEquals(1, serverStart.matchId());
        assertEquals(serverStart.configuration(), clientStart.configuration());
        assertEquals(42, clientStart.pieceSeed());
        assertEquals(Difficulty.HARD, clientStart.configuration().difficulty());
        for (int i = 0; i < 5; i++) { server.ready(); client.ready(); server.update(); client.update(); }
        assertTrue(server.takeStartedMatch().isEmpty());
        assertTrue(client.takeStartedMatch().isEmpty());
        assertEquals(1, serverWire.sent.stream().filter(e -> e.message() instanceof Start).count());
    }

    @Test void configurationChangesClearBothReadyFlagsAndStaleReadyCannotStart() {
        connect();
        server.ready(); client.ready(); // 클라이언트 준비가 서버에 아직 도착하지 않은 경합
        server.configure(Mode.TIMED, 120);
        server.update(); client.update();
        assertFalse(server.localReady()); assertFalse(server.remoteReady());
        assertFalse(client.localReady()); assertFalse(client.remoteReady());
        assertEquals(120, client.configuration().orElseThrow().timeLimitSeconds());
        client.configure(Mode.ITEM, 0);
        assertEquals(Mode.TIMED, client.configuration().orElseThrow().mode());
        client.ready(); server.update();
        assertTrue(server.remoteReady());
        server.configure(Mode.TIMED, 60); client.update();
        assertFalse(server.remoteReady()); assertFalse(client.localReady());
        server.ready(); client.ready(); server.update(); client.update();
        assertEquals(60, client.takeStartedMatch().orElseThrow().configuration().timeLimitSeconds());
    }

    @Test void duplicateAndWrongSenderMessagesCannotChangeLobby() {
        connect();
        clientWire.incoming.add(new MatchEnvelope(0, 1, new Lobby(1, Mode.NORMAL, Difficulty.HARD, 0)));
        serverWire.incoming.add(new MatchEnvelope(0, 100, new Lobby(99, Mode.ITEM, Difficulty.EASY, 0)));
        serverWire.incoming.add(new MatchEnvelope(1, 101, new Start(1, 7)));
        client.update(); server.update();
        assertEquals(Mode.NORMAL, server.configuration().orElseThrow().mode());
        assertEquals(P2pLobby.Stage.LOBBY, server.stage());
    }

    @ParameterizedTest @EnumSource(Mode.class)
    void rematchResetsReadinessAndPreservesConnectionSequenceAndMatchIds(Mode mode) {
        connect();
        server.configure(mode, mode == Mode.TIMED ? 180 : 0); client.update();
        server.ready(); client.ready(); server.update(); client.update();
        var s = server.takeStartedMatch().orElseThrow();
        var c = client.takeStartedMatch().orElseThrow();
        assertThrows(IllegalStateException.class, server::returnAfterResult);
        s.connection().send(s.matchId(), new Result(Outcome.DRAW, EndReason.TOP_OUT));
        assertInstanceOf(Result.class, c.connection().receive().orElseThrow().message());
        server.returnAfterResult(); client.returnAfterResult(); client.update();
        assertFalse(server.localReady()); assertFalse(server.remoteReady());
        assertFalse(client.localReady()); assertFalse(client.remoteReady());
        assertEquals(mode, client.configuration().orElseThrow().mode());
        server.ready(); client.update();
        assertEquals(P2pLobby.Stage.LOBBY, client.stage());
        client.ready(); server.update(); client.update();
        var next = client.takeStartedMatch().orElseThrow();
        assertEquals(2, next.matchId());
        assertSame(c.connection(), next.connection());
        long previous = 0;
        for (var e : serverWire.sent) { assertTrue(e.sequence() > previous); previous = e.sequence(); }
    }

    @Test void startLeavesFollowingGameMessagesForTheGameSession() {
        connect();
        client.ready(); server.update(); server.ready();
        var started = server.takeStartedMatch().orElseThrow();
        started.connection().send(started.matchId(), new Result(Outcome.SERVER_WINS, EndReason.TOP_OUT));
        client.update();
        var clientStart = client.takeStartedMatch().orElseThrow();
        assertEquals(MatchProtocol.Phase.PLAYING, clientStart.connection().protocol().phase());
        assertInstanceOf(Result.class, clientStart.connection().receive().orElseThrow().message());
    }

    @Test void failuresCancellationAndPeerLeaveCloseResources() {
        client.connect("192.168.1.2");
        clientWire.status = new MatchTransport.Status(MatchTransport.State.FAILED, "Connection refused", 0);
        client.update();
        assertEquals(P2pLobby.Stage.FAILED, client.stage());
        assertTrue(client.status().contains("Connection refused"));
        assertTrue(clientWire.closed); assertEquals(0, ips.writes);
        connect();
        serverWire.incoming.add(new MatchEnvelope(0, 1, new Leave()));
        server.update();
        assertEquals(P2pLobby.Stage.FAILED, server.stage());
        assertTrue(serverWire.closed);
        client.reset();
        assertEquals(P2pLobby.Stage.CHOICE, client.stage());
        assertTrue(clientWire.closed);
    }

    @Test void retryUsesFreshTransportAndDoesNotCarryConfiguration() {
        var wires = new java.util.ArrayDeque<Transport>();
        wires.add(serverWire); wires.add(clientWire);
        var retry = new P2pLobby(wires::remove, List::of, new RecentIps(ips), Difficulty.NORMAL, () -> 1);
        retry.host(); serverWire.connected(); retry.update(); retry.ready();
        retry.reset(); retry.host();
        assertTrue(serverWire.closed);
        assertEquals(MatchTransport.State.LISTENING, clientWire.status().state());
        assertFalse(retry.localReady()); assertTrue(retry.configuration().isEmpty());
        retry.close(); assertTrue(clientWire.closed);
    }
}
