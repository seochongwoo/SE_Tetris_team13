package team.tetris.ui.screen;

import java.awt.image.BufferedImage;
import java.util.EnumMap;
import java.util.List;
import java.util.OptionalLong;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import team.tetris.application.GameCommand;
import team.tetris.application.model.Difficulty;
import team.tetris.application.model.Settings;
import team.tetris.application.network.*;
import team.tetris.application.network.MatchMessage.*;
import team.tetris.application.port.StorageException;
import team.tetris.ui.TestApplication;
import team.tetris.ui.render.TextFramePanel;
import static org.junit.jupiter.api.Assertions.*;

class P2pMatchScreenTest {
    @Test void numpadKeyHintsAndLagWarningAreBothVisible() throws StorageException {
        var app = new TestApplication();
        var defaults = Settings.defaults();
        var keys = new EnumMap<>(defaults.keyBindings());
        keys.put(GameCommand.MOVE_LEFT, "NUMPAD4");
        keys.put(GameCommand.MOVE_RIGHT, "NUMPAD6");
        keys.put(GameCommand.ROTATE_CW, "NUMPAD8");
        keys.put(GameCommand.SOFT_DROP, "NUMPAD2");
        keys.put(GameCommand.HARD_DROP, "NUMPAD0");
        app.settings().update(new Settings(defaults.screenSize(), keys, false));
        try (var pair = new P2pMatchTestSupport.Pair(Mode.NORMAL, null, null)) {
            pair.st.rtt = OptionalLong.of(250_000_000L);
            var frame = new P2pMatchScreen(app.router(), pair.server, pair.s).render();
            // 긴 키 이름도 잘리지 않게 맨 아래 줄 전체를 쓰고, 지연은 상대 보드 바로 아래 줄에 둔다.
            assertTrue(frame.rowText(frame.rows() - 1).contains(
                    "NUMPAD4 NUMPAD6 이동  NUMPAD8 회전  NUMPAD2 내림  NUMPAD0 낙하"), frame.rowText(frame.rows() - 1));
            assertFalse(frame.rowText(frame.rows() - 1).contains("~"));
            String latency = frame.rowText(frame.rows() - 2);
            assertTrue(latency.contains("네트워크 지연 (랙) 250 ms"), latency);
            int right = team.tetris.ui.render.BattleRenderer.leftOf(team.tetris.application.battle.BattlePlayer.TWO) + 21;
            assertEquals('s', frame.codePointAt(right, frame.rows() - 2), "상대 보드 오른쪽 끝에 맞춤");
            assertEquals(' ', frame.codePointAt(right + 1, frame.rows() - 2));
            assertTrue(frame.rowText(0).contains("P 일시정지  Esc 메뉴"), frame.rowText(0));
        }
    }

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
                var playing = sr.render();
                assertEquals(78, playing.columns());
                assertEquals(2, playing.rowText(playing.rows() - 2).split("└" + "─".repeat(10) + "┘", -1).length - 1);
                assertEquals(team.tetris.ui.render.BattleRenderer.ROWS, playing.rows());
                assertTrue(playing.rowText(playing.rows() - 1).contains("낙하"));
                assertTrue(sr.render().text().contains("LV 0"));
                assertEquals(2, sr.render().text().split("NEXT", -1).length - 1);
                assertNotNull(sr.render().tileAt(2, 3)); assertNotNull(sr.render().tileAt(41, 3));
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
                sr.focusLost(); cr.update(0); assertFalse(c.match().paused());
                TestApplication.tap(sr, "ESCAPE"); cr.update(0); assertTrue(c.match().paused());
                assertTrue(sr.render().text().contains("일시정지"));
                assertTrue(sr.render().text().contains("► 재개"));
                assertTrue(cr.render().text().contains("상대가 재개할 때까지 대기"));
                assertTrue(cr.render().text().contains("► 기권"));
                assertFalse(cr.render().text().contains("► 재개"));
                TestApplication.tap(sr, sr.settings().keyBindings().get(GameCommand.QUIT_GAME)); cr.update(0);
                assertTrue(sr.render().text().contains("패배"));
                assertTrue(sr.render().text().contains("LOSE") && sr.render().text().contains("WIN"));
                assertTrue(cr.render().text().contains("승리"));
                sr.update(2_000_000_000L); cr.update(2_000_000_000L); cr.update(0);
                assertInstanceOf(P2pLobbyScreen.class, sr.current());
                assertInstanceOf(P2pLobbyScreen.class, cr.current());
                assertFalse(st.closed); assertFalse(ct.closed);
            }
            sr.requestExit(); assertTrue(st.closed); assertEquals(1, sa.exits());
        }
    }

    @Test void thePauseMenuIsChosenWithArrowsAndEnterLikeTheLocalBattle() {
        var app = new TestApplication();
        try (var pair = new P2pMatchTestSupport.Pair(Mode.NORMAL, null, null)) {
            var screen = new P2pMatchScreen(app.router(), pair.server, pair.s);
            press(screen, "ESCAPE");
            screen.update(0);
            assertTrue(pair.s.paused());
            assertEquals(List.of(P2pMatchScreen.RESUME_ITEM, P2pMatchScreen.FORFEIT_ITEM), screen.pauseItems());
            press(screen, "DOWN");
            assertEquals(1, screen.pauseCursor());
            press(screen, "UP");
            assertEquals(0, screen.pauseCursor());
            press(screen, "ENTER");
            screen.update(0);
            assertFalse(pair.s.paused(), "재개");
            press(screen, "ESCAPE");
            screen.update(0);
            assertEquals(0, screen.pauseCursor(), "다시 멈추면 맨 위에서 시작");
            press(screen, "DOWN");
            press(screen, "ENTER");
            screen.update(0);
            assertTrue(pair.s.result().isPresent(), "기권");
        }
    }

    private static void press(P2pMatchScreen screen, String key) {
        screen.onKeyPressed(key);
        screen.onKeyReleased(key);
    }
}
