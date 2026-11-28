package team.tetris.ui.screen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import team.tetris.application.model.Difficulty;
import team.tetris.application.network.LobbyTestSupport;
import team.tetris.application.network.MatchMessage.Mode;
import team.tetris.application.network.P2pLobby;
import team.tetris.application.network.RecentIps;
import team.tetris.ui.ScreenRouter;
import team.tetris.ui.TestApplication;
import team.tetris.ui.input.KeyNames;

/** 로비 채팅 화면 (도전 과제 CH-2): Tab으로 열고, 한글을 입력해 Enter로 보내면 상대 로비에 보인다. */
class P2pLobbyChatScreenTest {

    private final LobbyTestSupport.Transport serverTransport = new LobbyTestSupport.Transport();
    private final LobbyTestSupport.Transport clientTransport = new LobbyTestSupport.Transport();
    private final P2pLobby serverLobby = lobby(serverTransport);
    private final P2pLobby clientLobby = lobby(clientTransport);
    private final ScreenRouter server = new TestApplication().withP2pLobbies(() -> serverLobby).router();
    private final ScreenRouter client = new TestApplication().withP2pLobbies(() -> clientLobby).router();

    private static P2pLobby lobby(LobbyTestSupport.Transport transport) {
        return new P2pLobby(() -> transport, List::of, new RecentIps(new LobbyTestSupport.MemoryIps()),
                Difficulty.NORMAL, () -> 42);
    }

    @AfterEach
    void close() {
        serverLobby.close();
        clientLobby.close();
    }

    /** 두 로비를 연결해 경기 설정까지 주고받은 상태로 만든다. */
    private void connect() {
        serverTransport.peer = clientTransport;
        clientTransport.peer = serverTransport;
        server.showP2pLobby();
        client.showP2pLobby();
        serverLobby.host();
        clientLobby.connect("127.0.0.1");
        serverTransport.connected();
        clientTransport.connected();
        server.update(0);
        client.update(0);
    }

    private static P2pLobbyScreen screen(ScreenRouter router) {
        return assertInstanceOf(P2pLobbyScreen.class, router.current());
    }

    private static void type(ScreenRouter router, String text) {
        text.chars().forEach(c -> router.charTyped((char) c));
    }

    @Test
    void tabOpensChatAndEnterSendsKoreanTextToTheOtherLobby() {
        connect();
        assertTrue(server.render().text().contains("아직 메시지가 없습니다"));
        assertTrue(server.render().text().contains("Tab을 눌러 메시지 입력"));
        assertTrue(server.render().text().contains("Enter 게임 시작 · Tab 채팅 · Esc 나가기"));
        assertFalse(server.acceptsTextInput());

        TestApplication.tap(server, "TAB");
        assertTrue(screen(server).chatOpen());
        assertTrue(server.acceptsTextInput(), "채팅 중에만 입력기를 켜 한글을 받는다");
        type(server, "안녕 gg");
        TestApplication.tap(server, KeyNames.BACK_SPACE);
        type(server, "!");
        assertEquals("안녕 g!", screen(server).chatInput());
        assertTrue(server.render().text().contains("› 안녕 g!▏"), "입력 줄과 깜빡이는 커서");
        server.update(team.tetris.ui.screen.P2pLobbyScreen.CURSOR_BLINK_NANOS);
        assertTrue(server.render().text().contains("› 안녕 g! "), "커서가 꺼진 순간");
        assertTrue(server.render().text().contains("Enter 보내기 · Esc 닫기 · 한/영 한글"));

        TestApplication.tap(server, KeyNames.ENTER);
        client.update(0);

        assertFalse(screen(server).chatOpen());
        assertFalse(serverLobby.localReady(), "채팅을 보낸 Enter는 게임 시작이 아니다");
        assertTrue(server.render().text().contains("나 안녕 g!"));
        assertTrue(client.render().text().contains("상대 안녕 g!"), client.render().text());
    }

    @Test
    void whileTypingLobbyKeysDoNothingAndEscapeOnlyClosesTheChat() {
        connect();
        TestApplication.tap(server, "TAB");
        TestApplication.tap(server, "RIGHT");
        TestApplication.tap(server, "UP");
        assertEquals(Mode.NORMAL, serverLobby.configuration().orElseThrow().mode(), "채팅 중에는 설정을 바꾸지 않는다");
        assertEquals(Difficulty.NORMAL, serverLobby.configuration().orElseThrow().difficulty());

        type(server, "abc");
        TestApplication.tap(server, KeyNames.ESCAPE);
        assertFalse(screen(server).chatOpen());
        assertEquals(P2pLobby.Stage.LOBBY, serverLobby.stage(), "Esc는 로비를 나가지 않고 채팅만 닫는다");
        assertTrue(serverLobby.chat().isEmpty(), "닫으면 보내지 않는다");

        TestApplication.tap(server, "TAB");
        assertEquals("", screen(server).chatInput(), "다시 열면 빈 입력");
        TestApplication.tap(server, KeyNames.ENTER);
        assertTrue(serverLobby.chat().isEmpty(), "빈 입력은 보내지 않는다");
    }

    @Test
    void theInputStopsAtTheWidthLimitAndOnlyTheLatestLinesAreShown() {
        connect();
        TestApplication.tap(client, "TAB");
        type(client, "가".repeat(20));
        assertEquals("가".repeat(P2pLobbyScreen.CHAT_INPUT_WIDTH / 2), screen(client).chatInput());
        TestApplication.tap(client, KeyNames.ENTER);

        for (int i = 1; i <= 5; i++) {
            serverLobby.sendChat("line " + i);
        }
        client.update(0);
        String text = client.render().text();
        // 입력 줄이 닫혀 있으면 최근 CHAT_ROWS(4)줄을 보여 준다.
        assertFalse(text.contains("line 1"), text);
        for (int i = 2; i <= 5; i++) {
            assertTrue(text.contains("상대 line " + i), text);
        }
        assertFalse(text.contains("나 가가"), "오래된 내 줄은 밀려난다");
    }

    @Test
    void tabDoesNothingBeforeTheLobbyIsConnected() {
        server.showP2pLobby();
        TestApplication.tap(server, "TAB");
        assertFalse(screen(server).chatOpen());
        assertFalse(server.render().text().contains("Tab 채팅"));
        assertFalse(server.render().text().contains("Tab을 눌러 메시지 입력"));
    }

    @Test
    void anOpenChatClosesWhenTheConnectionDrops() {
        connect();
        TestApplication.tap(server, "TAB");
        type(server, "hi");
        serverTransport.close();
        server.update(0);
        assertFalse(screen(server).chatOpen());
        assertFalse(server.acceptsTextInput());
    }
}
