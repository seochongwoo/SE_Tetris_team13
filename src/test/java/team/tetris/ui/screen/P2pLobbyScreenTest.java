package team.tetris.ui.screen;

import java.util.List;
import org.junit.jupiter.api.Test;
import team.tetris.application.model.Difficulty;
import team.tetris.application.network.*;
import team.tetris.application.network.MatchMessage.*;
import team.tetris.application.network.LobbyTestSupport.*;
import team.tetris.application.port.MatchTransport;
import team.tetris.ui.ScreenRouter;
import team.tetris.ui.TestApplication;
import static org.junit.jupiter.api.Assertions.*;

class P2pLobbyScreenTest {
    private final Transport transport = new Transport();
    private final MemoryIps ips = new MemoryIps();
    private final P2pLobby lobby = new P2pLobby(() -> transport,
            () -> List.of("10.0.0.1", "10.0.0.2", "10.0.0.3", "10.0.0.4", "10.0.0.5", "192.168.0.6"),
            new RecentIps(ips), Difficulty.NORMAL, () -> 42);
    private final TestApplication app = new TestApplication().withP2pLobbies(() -> lobby);
    private final ScreenRouter router = app.router();
    private void tap(String key) { TestApplication.tap(router, key); }
    private void enter() {
        for (int i = 0; i < 2; i++) tap("DOWN");
        tap("ENTER");
        assertInstanceOf(P2pLobbyScreen.class, router.current());
    }

    @Test void menuServerAddressPagingAndWindowClose() {
        enter(); tap("ENTER");
        assertTrue(router.render().text().contains("접속 대기 중"));
        assertTrue(router.render().text().contains("10.0.0.1"));
        assertFalse(router.render().text().contains("192.168.0.6"));
        tap("DOWN");
        assertTrue(router.render().text().contains("192.168.0.6"));
        tap("UP");
        assertTrue(router.render().text().contains("10.0.0.1"));
        router.requestExit();
        assertTrue(transport.closed); assertEquals(1, app.exits());
    }

    @Test void clientInputValidationConnectionAndServerOnlyConfiguration() {
        enter(); tap("DOWN"); tap("ENTER");
        for (char c : "999.1.1.1".toCharArray()) router.charTyped(c);
        tap("ENTER");
        assertTrue(router.render().text().contains("IPv4 주소를 입력하세요"));
        tap("DELETE");
        for (char c : "127.0.0.12".toCharArray()) router.charTyped(c);
        tap("BACKSPACE"); router.charTyped('x');
        assertEquals("127.0.0.1", ((P2pLobbyScreen) router.current()).input());
        tap("ENTER");
        assertTrue(router.render().text().contains("접속 중"));
        transport.connected();
        transport.incoming.add(new MatchEnvelope(0, 1, new Lobby(1, Mode.TIMED, Difficulty.HARD, 180)));
        router.update(1);
        assertTrue(router.render().text().contains("연결됨"));
        assertTrue(router.render().text().contains("3분"));
        tap("LEFT"); tap("UP");
        assertEquals(Mode.TIMED, lobby.configuration().orElseThrow().mode());
        assertEquals(180, lobby.configuration().orElseThrow().timeLimitSeconds());
        tap("ENTER");
        assertTrue(router.render().text().contains("나: 준비 완료"));
        tap("ESCAPE");
        assertTrue(transport.closed); assertEquals(P2pLobby.Stage.CHOICE, lobby.stage());
        tap("ESCAPE"); assertInstanceOf(MenuScreen.class, router.current());
    }

    @Test void serverControlsDefaultTimeAndReadyResetAndStartedDisplay() {
        enter(); tap("ENTER"); transport.connected(); router.update(1);
        tap("RIGHT");
        assertEquals(Mode.ITEM, lobby.configuration().orElseThrow().mode());
        tap("RIGHT");
        assertTrue(router.render().text().contains("2분"));
        tap("ENTER"); tap("UP");
        assertFalse(lobby.localReady());
        assertTrue(router.render().text().contains("3분"));
        tap("DOWN"); tap("DOWN");
        assertTrue(router.render().text().contains("1분"));
        tap("ENTER");
        long revision = lobby.configuration().orElseThrow().revision();
        transport.incoming.add(new MatchEnvelope(0, 1, new Ready(revision, true)));
        router.update(1);
        assertEquals(P2pLobby.Stage.STARTED, lobby.stage());
        assertTrue(router.render().text().contains("양쪽 준비 완료"));
        var screen = (P2pLobbyScreen) router.current();
        var started = screen.handoffStartedMatch().orElseThrow();
        assertTrue(screen.handoffStartedMatch().isEmpty());
        router.showMenu(); // 카드 24가 경기 화면으로 교체해도 기존 화면이 연결을 닫지 않는다.
        assertFalse(transport.closed);
        started.connection().close();
        assertTrue(transport.closed);
    }

    @Test void selectingRecentIpAndHeldEnterDoNotSkipConnectionSteps() {
        lobby.recent().connected("10.0.0.2");
        enter(); tap("DOWN");
        router.keyPressed("ENTER"); router.keyPressed("ENTER");
        assertEquals(P2pLobby.Stage.CHOICE, lobby.stage());
        router.keyReleased("ENTER");
        tap("DOWN");
        assertEquals("10.0.0.2", ((P2pLobbyScreen) router.current()).input());
        tap("ENTER");
        assertEquals("10.0.0.2", transport.host);
    }

    @Test void failureShowsReasonAndRetryOrNavigationDisposesTransport() {
        enter(); tap("ENTER");
        transport.status = new MatchTransport.Status(MatchTransport.State.FAILED, "Address already in use", 0);
        router.update(1);
        assertTrue(router.render().text().contains("Address already in use"));
        assertTrue(transport.closed);
        tap("ENTER");
        assertEquals(P2pLobby.Stage.CHOICE, lobby.stage());
        router.showMenu();
        assertEquals(P2pLobby.Stage.CLOSED, lobby.stage());
    }
}
