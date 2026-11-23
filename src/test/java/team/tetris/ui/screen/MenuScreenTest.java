package team.tetris.ui.screen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import team.tetris.application.model.Settings;
import team.tetris.application.port.SettingsRepository;
import team.tetris.application.port.StorageException;
import team.tetris.storage.memory.InMemoryScoreRepository;
import team.tetris.ui.ScreenRouter;
import team.tetris.ui.TestApplication;
import team.tetris.ui.render.Backdrop;
import team.tetris.ui.render.TextFrame;

class MenuScreenTest {

    private final TestApplication app = new TestApplication();
    private final ScreenRouter router = app.router();

    /** 키를 한 번 눌렀다 뗀다. */
    private void tap(String key) {
        TestApplication.tap(router, key);
    }

    private MenuScreen menu() {
        return (MenuScreen) router.current();
    }

    @Test
    void showsTheGameNameAndAllMenuItems() {
        String text = router.render().text();

        assertTrue(text.contains("█"));
        assertTrue(text.contains("SE Tetris"));
        for (String label : List.of("일반 모드", "아이템 모드", "대전 모드", "P2P 대전", "설정", "스코어보드", "종료")) {
            assertTrue(text.contains(label), label);
        }
        assertTrue(text.contains("이동") && text.contains("Enter") && text.contains("선택"));
    }

    @Test
    void arrowKeysMoveTheCursorAndWrapAround() {
        tap("DOWN");
        assertEquals(1, menu().cursor());

        tap("UP");
        tap("UP");
        assertEquals(menu().items().size() - 1, menu().cursor());
    }

    @Test
    void enterRunsTheSelectedItem() {
        tap("ENTER");
        assertInstanceOf(GameScreen.class, router.current());

        router.showMenu();
        tap("DOWN");
        tap("ENTER");
        assertInstanceOf(GameScreen.class, router.current()); // 아이템 모드

        router.showMenu();
        tap("DOWN");
        tap("DOWN");
        tap("ENTER");
        assertEquals(router.battleModeItems().size(), menu().items().size()); // 대전 모드 선택

        router.showMenu();
        tap("DOWN");
        tap("DOWN");
        tap("DOWN");
        tap("DOWN");
        tap("ENTER");
        assertInstanceOf(SettingsScreen.class, router.current());

        router.showMenu();
        tap("DOWN");
        tap("DOWN");
        tap("DOWN");
        tap("DOWN");
        tap("DOWN");
        tap("ENTER");
        assertInstanceOf(ScoreboardScreen.class, router.current());

        router.showMenu();
        tap("UP");
        tap("ENTER");
        assertEquals(1, app.exits());
    }

    @Test
    void otherKeysShowTheKeysThatCanBeUsed() {
        assertFalse(router.render().text().contains("사용할 수 있는 키"));

        tap("A");

        assertTrue(router.render().text().contains("사용할 수 있는 키"));
    }

    @Test
    void showsANoticeWhenSettingsCouldNotBeRead() {
        SettingsRepository broken = new SettingsRepository() {
            @Override
            public Optional<Settings> load() throws StorageException {
                throw new StorageException(StorageException.Kind.INVALID_DATA, Path.of("settings.properties"), null);
            }

            @Override
            public void save(Settings settings) {
            }
        };
        ScreenRouter withNotice = new TestApplication(broken, new InMemoryScoreRepository()).router();

        assertTrue(withNotice.render().text().contains("설정을 읽지 못해"));
    }

    @Test
    void requiresAtLeastOneItem() {
        assertThrows(IllegalArgumentException.class, () -> new MenuScreen(router, List.of()));
    }

    @Test
    void cathedralBackdropAdvancesWithTime() {
        assertEquals(Backdrop.cathedral(0), router.render().backdrop());

        router.update(1_500_000_000L);

        assertEquals(Backdrop.cathedral(1_500_000_000L), router.render().backdrop());
    }

    @Test
    void menuSitsOnAPanelBelowTheSkyWithOneHintBandAboveTheBottomEdge() {
        var frame = router.render();
        int itemsTop = menu().rowOf(0);

        var panel = frame.plates().stream().filter(plate -> plate.x == MenuScreen.PANEL_LEFT).findFirst().orElseThrow();
        assertTrue(panel.y > 6); // 부제 아래
        assertTrue(panel.y + panel.height <= 21); // 배경의 땅 위
        var panelRows = java.util.stream.IntStream.range(panel.y, panel.y + panel.height)
                .mapToObj(frame::rowText).toList();
        for (var item : menu().items()) {
            assertTrue(panelRows.stream().anyMatch(row -> row.contains(item.label())), item.label());
        }
        assertTrue(frame.plates().stream().anyMatch(plate -> plate.y == MenuScreen.HINT_ROW));
        assertTrue(MenuScreen.HINT_ROW < TextFrame.ROWS - 1);
        assertTrue(frame.rowText(MenuScreen.HINT_ROW).contains(" Enter  선택"));
        assertTrue(frame.rowText(itemsTop).contains(menu().items().get(0).label()));
        assertTrue(frame.rowText(itemsTop).contains("►"));
    }

    @Test
    void subMenusShowWhatIsBeingChosenAboveTheirItemsInsideThePanel() {
        // 대전 모드와 제한 시간 선택은 시작 메뉴와 같은 상자를 쓰므로 무엇을 고르는지 제목으로 알려 준다.
        for (Runnable open : List.<Runnable>of(router::showBattleModes, router::showTimeLimits)) {
            open.run();
            var screen = (MenuScreen) router.current();
            String heading = screen.heading().orElseThrow();
            var frame = router.render();
            int itemsTop = MenuScreen.itemsTop(screen.items().size(), true);
            var panel = frame.plates().stream().filter(plate -> plate.x == MenuScreen.PANEL_LEFT).findFirst()
                    .orElseThrow();
            assertTrue(frame.rowText(itemsTop - 2).contains(heading), frame.rowText(itemsTop - 2));
            assertTrue(panel.y < itemsTop - 2 && panel.y > 6, "제목도 상자 안, 부제 아래");
            assertTrue(panel.y + panel.height <= 21, "배경의 땅 위");
            assertTrue(frame.rowText(itemsTop + 2 * screen.cursor()).contains("►"));
        }
        router.showBattleModes();
        assertEquals("대전 모드 선택", ((MenuScreen) router.current()).heading().orElseThrow());
        router.showTimeLimits();
        assertEquals("제한 시간 선택", ((MenuScreen) router.current()).heading().orElseThrow());
        router.showMenu();
        assertTrue(menu().heading().isEmpty(), "시작 메뉴는 게임 이름이 제목");
    }

    @Test
    void theStartMenuLeavesABlankRowBetweenItsGroups() {
        // 일반·아이템 / 대전·P2P / 설정·스코어보드·종료 사이에 빈 줄을 둔다.
        var frame = router.render();
        List<Integer> rows = java.util.stream.IntStream.range(0, menu().items().size()).map(menu()::rowOf)
                .boxed().toList();
        assertEquals(List.of(1, 2, 1, 2, 1, 1), java.util.stream.IntStream.range(1, rows.size())
                .map(i -> rows.get(i) - rows.get(i - 1)).boxed().toList());
        for (int i = 0; i < rows.size(); i++) {
            assertTrue(frame.rowText(rows.get(i)).contains(menu().items().get(i).label()));
        }
        for (int gapAfter : List.of(1, 3)) {
            assertTrue(frame.rowText(rows.get(gapAfter) + 1).substring(MenuScreen.PANEL_LEFT + 1,
                    MenuScreen.PANEL_LEFT + MenuScreen.PANEL_WIDTH - 1).isBlank());
        }
        assertThrows(IllegalArgumentException.class,
                () -> new MenuScreen(router, menu().items(), java.util.Set.of(0)));
    }

    @Test
    void wrongKeyHintReplacesTheHintBand() {
        tap("A");

        var hint = router.render().rowText(MenuScreen.HINT_ROW);

        assertTrue(hint.contains("사용할 수 있는 키"));
        assertFalse(hint.contains("선택"));
    }

    @Test
    void selectionMarkerStaysWhileTimePasses() {
        int row = menu().rowOf(0);

        router.update(1_234_000_000L);

        assertTrue(router.render().rowText(row).contains("►"));
    }

    @Test
    void keyNamesInTheHintSitOnSeparateKeycaps() {
        var frame = router.render();
        String hint = frame.rowText(MenuScreen.HINT_ROW);
        int enter = hint.indexOf("Enter");
        int arrow = hint.indexOf('↑');
        var base = frame.styleAt(0, 0).background();

        // 키 이름과 양옆 한 칸은 키캡 바탕, 키캡 사이와 설명은 기본 바탕이다. (↑ 앞의 글자는 모두 한 칸 폭)
        assertTrue(!frame.styleAt(arrow, MenuScreen.HINT_ROW).background().equals(base));
        assertTrue(!frame.styleAt(arrow - 1, MenuScreen.HINT_ROW).background().equals(base));
        assertEquals(base, frame.styleAt(arrow - 2, MenuScreen.HINT_ROW).background());
        String row = frame.rowText(MenuScreen.HINT_ROW);
        int enterCol = TextFrame.displayWidth(row.substring(0, enter));
        assertTrue(!frame.styleAt(enterCol, MenuScreen.HINT_ROW).background().equals(base));
        assertTrue(!frame.styleAt(enterCol + 5, MenuScreen.HINT_ROW).background().equals(base));
        assertEquals(base, frame.styleAt(enterCol + 6, MenuScreen.HINT_ROW).background());
    }
}
