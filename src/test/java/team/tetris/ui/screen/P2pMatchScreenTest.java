package team.tetris.ui.screen;

import java.util.List;
import java.awt.image.BufferedImage;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import team.tetris.application.GameCommand;
import team.tetris.application.model.Difficulty;
import team.tetris.application.network.*;
import team.tetris.application.network.MatchMessage.*;
import team.tetris.ui.TestApplication;
import team.tetris.ui.render.TextFramePanel;
import static org.junit.jupiter.api.Assertions.*;

class P2pMatchScreenTest {
    @ParameterizedTest @EnumSource(Mode.class)
    void routersRenderTwoBoardsResultReturnAndSecondGame(Mode mode) {
        var st = new LobbyTestSupport.Transport(); var ct = new LobbyTestSupport.Transport();
        st.peer = ct; ct.peer = st;
        try (var sl = new P2pLobby(() -> st, List::of, new RecentIps(new LobbyTestSupport.MemoryIps()), Difficulty.NORMAL, () -> 42);
             var cl = new P2pLobby(() -> ct, List::of, new RecentIps(new LobbyTestSupport.MemoryIps()), Difficulty.NORMAL, () -> 42)) {
            var sa = new TestApplication().withP2pLobbies(() -> sl);
            var ca = new TestApplication().withP2pLobbies(() -> cl);
            var sr = sa.router(); var cr = ca.router(); sr.showP2pLobby(); cr.showP2pLobby();
            sl.host(); cl.connect("127.0.0.1"); st.connected(); ct.connected(); sr.update(0); cr.update(0);
            sl.configure(mode, mode == Mode.TIMED ? 60 : 0); cr.update(0);
            for (int round = 1; round <= 2; round++) {
                TestApplication.tap(sr, "ENTER"); TestApplication.tap(cr, "ENTER"); sr.update(0); cr.update(0); sr.update(0);
                var s = assertInstanceOf(P2pMatchScreen.class, sr.current());
                var c = assertInstanceOf(P2pMatchScreen.class, cr.current());
                assertEquals(round, s.match().matchId());
                assertEquals(88, sr.render().columns());
                assertEquals(2, sr.render().text().split("NEXT", -1).length - 1);
                assertNotNull(sr.render().tileAt(2, 3)); assertNotNull(sr.render().tileAt(46, 3));
                // 실제 paint 콜백과 3단계 화면 크기에서도 넓은 프레임을 그릴 수 있다.
                for (var size : team.tetris.application.model.Settings.ScreenSize.values()) {
                    var panel = new TextFramePanel(size, sr.render());
                    panel.setSize(panel.getPreferredSize());
                    var image = new BufferedImage(panel.getWidth(), panel.getHeight(), BufferedImage.TYPE_INT_RGB);
                    var graphics = image.createGraphics();
                    try { panel.paint(graphics); } finally { graphics.dispose(); }
                }
                String left = sr.settings().keyBindings().get(GameCommand.MOVE_LEFT);
                var active = s.match().local().active(); TestApplication.tap(sr, left); cr.update(0);
                assertNotEquals(active, s.match().local().active());
                assertEquals(s.match().local(), c.match().remote().orElseThrow());
                sr.focusLost(); cr.update(0); assertTrue(c.match().paused());
                TestApplication.tap(sr, sr.settings().keyBindings().get(GameCommand.QUIT_GAME)); cr.update(0);
                assertTrue(sr.render().text().contains("클라이언트 승리"));
                assertTrue(cr.render().text().contains("클라이언트 승리"));
                assertEquals("클라이언트 승리", sr.render().rowText(24).strip());
                assertEquals("클라이언트 승리", cr.render().rowText(24).strip());
                sr.update(2_000_000_000L); cr.update(2_000_000_000L); cr.update(0);
                assertInstanceOf(P2pLobbyScreen.class, sr.current());
                assertInstanceOf(P2pLobbyScreen.class, cr.current());
                assertFalse(st.closed); assertFalse(ct.closed);
            }
            sr.requestExit(); assertTrue(st.closed); assertEquals(1, sa.exits());
        }
    }
}
