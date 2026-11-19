package team.tetris.ui.screen;

import java.util.List;
import java.util.OptionalLong;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.EnumSource;
import team.tetris.application.GameCommand;
import team.tetris.application.model.Difficulty;
import team.tetris.application.network.*;
import team.tetris.application.network.LobbyTestSupport.*;
import team.tetris.application.network.MatchMessage.*;
import team.tetris.application.port.MatchTransport;
import team.tetris.ui.TestApplication;
import static org.junit.jupiter.api.Assertions.*;

class P2pNetworkFailureScreenTest {
    enum Phase { LOBBY, PLAYING, PAUSED, RESULT }

    static Stream<Arguments> phasesAndRoles() {
        return Stream.of(Phase.values()).flatMap(phase -> Stream.of(Role.values()).map(role -> Arguments.of(phase, role)));
    }

    private static P2pLobby lobby(Transport transport) {
        return new P2pLobby(() -> transport, List::of, new RecentIps(new MemoryIps()), Difficulty.NORMAL, () -> 42);
    }

    @ParameterizedTest @MethodSource("phasesAndRoles")
    void lagRecoveryAndDisconnectReturnToChoiceInEveryConnectedPhase(Phase phase, Role role) {
        var st = new Transport(); var ct = new Transport(); st.peer = ct; ct.peer = st;
        var sl = lobby(st); var cl = lobby(ct);
        var serverLobbies = new java.util.ArrayDeque<>(List.of(sl, lobby(new Transport())));
        var clientLobbies = new java.util.ArrayDeque<>(List.of(cl, lobby(new Transport())));
        var sr = new TestApplication().withP2pLobbies(serverLobbies::remove).router();
        var cr = new TestApplication().withP2pLobbies(clientLobbies::remove).router();
        sr.showP2pLobby(); cr.showP2pLobby();
        sl.host(); cl.connect("127.0.0.1"); st.connected(); ct.connected(); sr.update(0); cr.update(0);
        sl.configure(Mode.TIMED, 60); cr.update(0);
        if (phase != Phase.LOBBY) {
            TestApplication.tap(sr, "ENTER"); TestApplication.tap(cr, "ENTER");
            sr.update(0); cr.update(0); sr.update(0);
            if (phase == Phase.PAUSED || phase == Phase.RESULT) {
                sr.focusLost(); cr.update(0);
                if (phase == Phase.RESULT) {
                    TestApplication.tap(sr, sr.settings().keyBindings().get(GameCommand.QUIT_GAME));
                    cr.update(0);
                }
            }
        }
        var router = role == Role.SERVER ? sr : cr;
        var transport = role == Role.SERVER ? st : ct;
        try {
            transport.rtt = OptionalLong.of(200_000_001L);
            router.update(0);
            assertTrue(router.render().text().contains("네트워크 지연 (랙)"));
            transport.rtt = OptionalLong.of(200_000_000L);
            router.update(0);
            assertFalse(router.render().text().contains("네트워크 지연 (랙)"));
            transport.status = new MatchTransport.Status(MatchTransport.State.FAILED, NetworkPolicy.NO_RESPONSE, 21313);
            router.update(0);
            var failedScreen = router.current();
            assertTrue(transport.closed);
            assertTrue(router.render().text().contains("3초 동안 상대 응답"));
            assertFalse(router.render().text().contains("네트워크 지연 (랙)"));
            if (failedScreen instanceof P2pMatchScreen screen) {
                var frozen = screen.match().local();
                long remaining = screen.match().remainingNanos();
                TestApplication.tap(router, router.settings().keyBindings().get(GameCommand.HARD_DROP));
                router.update(1_999_999_999L);
                assertEquals(frozen, screen.match().local());
                assertEquals(remaining, screen.match().remainingNanos());
            } else router.update(1_999_999_999L);
            assertSame(failedScreen, router.current());
            router.update(1);
            var returned = assertInstanceOf(P2pLobbyScreen.class, router.current());
            assertEquals(P2pLobby.Stage.CHOICE, returned.lobby().stage());
            assertFalse(router.render().text().contains("3초 동안 상대 응답"));
            assertTrue(router.render().text().contains("클라이언트"));
        } finally { sr.requestExit(); cr.requestExit(); sl.close(); cl.close(); }
    }

    @org.junit.jupiter.api.Test void peerDisconnectShowsOutcomeReasonAndDestination() {
        try (var pair = new P2pMatchTestSupport.Pair(Mode.NORMAL, null, null)) {
            var screen = new P2pMatchScreen(new TestApplication().router(), pair.server, pair.s);
            pair.st.status = new MatchTransport.Status(MatchTransport.State.FAILED, "상대 연결 종료", 0, true);
            screen.update(0);
            String overlay = screen.render().text();
            assertTrue(overlay.contains("승리"));
            assertTrue(overlay.contains("상대 연결 종료"));
            assertTrue(overlay.contains("서버/클라이언트 선택"));
        }
    }

    @ParameterizedTest @EnumSource(value = MatchTransport.State.class, names = {"CONNECTING", "HANDSHAKING"})
    void connectionErrorsAlsoAutoReturnAndCanBeAcknowledgedEarly(MatchTransport.State state) {
        var transport = new Transport();
        var lobby = lobby(transport);
        var router = new TestApplication().withP2pLobbies(() -> lobby).router();
        router.showP2pLobby(); lobby.connect("127.0.0.1");
        transport.status = new MatchTransport.Status(state, "", 0);
        router.update(0);
        transport.status = new MatchTransport.Status(MatchTransport.State.FAILED, "연결 실패", 0);
        router.update(0);
        assertTrue(router.render().text().contains("연결 실패"));
        assertTrue(transport.closed);
        TestApplication.tap(router, "ENTER");
        assertEquals(P2pLobby.Stage.CHOICE, lobby.stage());
        router.requestExit();
    }
}
