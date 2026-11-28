package team.tetris.application.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;
import team.tetris.application.model.Difficulty;
import team.tetris.application.network.LobbyTestSupport.MemoryIps;
import team.tetris.application.network.LobbyTestSupport.Transport;
import team.tetris.application.network.MatchMessage.Chat;
import team.tetris.application.network.MatchMessage.Lobby;
import team.tetris.application.network.MatchMessage.Mode;
import team.tetris.application.network.MatchMessage.Ready;
import team.tetris.application.network.MatchMessage.Role;
import team.tetris.application.network.MatchMessage.Start;
import team.tetris.application.network.P2pLobby.ChatLine;

/** 로비 채팅 (도전 과제 CH-2): 연결된 로비에서만 주고받고, 경기 중에는 받지 않는다. */
class P2pChatTest {

    private final Transport serverTransport = new Transport();
    private final Transport clientTransport = new Transport();
    private final P2pLobby server = lobby(serverTransport);
    private final P2pLobby client = lobby(clientTransport);

    private static P2pLobby lobby(Transport transport) {
        return new P2pLobby(() -> transport, List::of, new RecentIps(new MemoryIps()), Difficulty.NORMAL, () -> 42);
    }

    /** 두 로비를 연결하고 서버의 첫 설정을 클라이언트까지 전달한다. */
    private void connect() {
        serverTransport.peer = clientTransport;
        clientTransport.peer = serverTransport;
        server.host();
        client.connect("127.0.0.1");
        serverTransport.connected();
        clientTransport.connected();
        server.update();
        client.update();
    }

    @Test
    void connectedLobbiesExchangeChatBothWays() {
        connect();
        assertTrue(server.canChat() && client.canChat());

        assertTrue(client.sendChat("  안녕하세요  "));
        assertTrue(server.sendChat("ready?"));
        server.update();
        client.update();

        assertEquals(List.of(new ChatLine(true, "ready?"), new ChatLine(false, "안녕하세요")), server.chat());
        assertEquals(List.of(new ChatLine(true, "안녕하세요"), new ChatLine(false, "ready?")), client.chat());
    }

    @Test
    void emptyTextIsNotSentAndLongTextIsCutToTheLimit() {
        connect();
        assertFalse(server.sendChat("   "));
        assertFalse(server.sendChat(null));
        assertTrue(server.sendChat("가".repeat(40)));
        client.update();
        assertEquals("가".repeat(Chat.MAX_CHAT_LENGTH), client.chat().get(0).text());
        assertEquals(1, serverTransport.sent.stream().filter(e -> e.message() instanceof Chat).count());
    }

    @Test
    void chatNeedsAConnectedLobbyWithSettings() {
        assertFalse(server.canChat());
        assertFalse(server.sendChat("hi"));
        server.host();
        assertFalse(server.sendChat("hi"), "아직 연결 전");
        // 클라이언트는 서버의 첫 설정을 받기 전까지 보내지 않는다.
        client.connect("127.0.0.1");
        clientTransport.connected();
        client.update();
        assertEquals(P2pLobby.Stage.LOBBY, client.stage());
        assertFalse(client.canChat());
        assertTrue(clientTransport.sent.isEmpty());
    }

    @Test
    void onlyTheMostRecentChatIsKeptAndANewConnectionStartsEmpty() {
        connect();
        for (int i = 0; i < P2pLobby.CHAT_HISTORY + 5; i++) {
            server.sendChat("msg " + i);
        }
        assertEquals(P2pLobby.CHAT_HISTORY, server.chat().size());
        assertEquals("msg 5", server.chat().get(0).text());
        server.reset();
        assertTrue(server.chat().isEmpty());
    }

    @Test
    void chatThatArrivesDuringAMatchIsIgnoredAndCannotBeSent() {
        connect();
        server.ready();
        client.ready();
        client.update();
        server.update();
        client.update();
        assertEquals(P2pLobby.Stage.STARTED, server.stage());
        assertFalse(server.canChat());
        assertFalse(server.sendChat("too late"));

        // 경기 중인 프로토콜은 채팅을 받지 않는다.
        var protocol = new MatchProtocol();
        assertEquals(MatchProtocol.Decision.ACCEPTED,
                protocol.accept(Role.SERVER, new MatchEnvelope(0, 1, new Lobby(1, Mode.NORMAL, Difficulty.NORMAL, 0))));
        assertEquals(MatchProtocol.Decision.ACCEPTED, protocol.accept(Role.CLIENT, new MatchEnvelope(0, 1, new Chat("hi"))));
        assertEquals(MatchProtocol.Decision.STALE_MATCH,
                protocol.accept(Role.CLIENT, new MatchEnvelope(3, 2, new Chat("hi"))), "채팅은 경기 번호 0");
        protocol.accept(Role.SERVER, new MatchEnvelope(0, 2, new Ready(1, true)));
        protocol.accept(Role.CLIENT, new MatchEnvelope(0, 3, new Ready(1, true)));
        assertEquals(MatchProtocol.Decision.ACCEPTED, protocol.accept(Role.SERVER, new MatchEnvelope(1, 3, new Start(1, 7))));
        assertEquals(MatchProtocol.Decision.WRONG_STATE, protocol.accept(Role.CLIENT, new MatchEnvelope(0, 4, new Chat("hi"))));
    }

    @Test
    void chatTextIsValidated() {
        assertEquals("hi", new Chat("  hi ").text());
        assertThrows(IllegalArgumentException.class, () -> new Chat(" "));
        assertThrows(IllegalArgumentException.class, () -> new Chat("a\tb"));
        assertThrows(IllegalArgumentException.class, () -> new Chat("x".repeat(Chat.MAX_CHAT_LENGTH + 1)));
        assertThrows(NullPointerException.class, () -> new Chat(null));
        assertEquals(4, MatchMessage.PROTOCOL_VERSION, "채팅을 모르는 이전 버전과는 연결하지 않는다");
    }
}
