package team.tetris.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;
import team.tetris.application.model.Settings;
import team.tetris.core.TetrominoType;
import team.tetris.ui.screen.GameScreen;
import team.tetris.ui.screen.MenuScreen;
import team.tetris.ui.screen.NameEntryScreen;
import team.tetris.ui.screen.ScoreboardScreen;

class ScreenRouterTest {

    private final TestApplication app = new TestApplication().withPieces(TetrominoType.O);
    private final ScreenRouter router = app.router();

    @Test
    void startsAtTheMenuWithTheRequiredItems() {
        assertInstanceOf(MenuScreen.class, router.current());
        assertEquals(List.of("게임 시작", "설정", "스코어보드", "종료"),
                router.menuItems().stream().map(MenuScreen.MenuItem::label).toList());
    }

    @Test
    void windowCloseOnTheMenuExitsOnceImmediately() {
        router.requestExit();
        router.exit();

        assertEquals(1, app.exits());
        assertTrue(router.hasExited());
    }

    @Test
    void windowCloseDuringNameEntryWaitsForConfirmation() {
        router.startGame();
        TestApplication.playUntilGameOver(router);

        router.requestExit();

        assertInstanceOf(NameEntryScreen.class, router.current());
        assertEquals(0, app.exits());
    }

    @Test
    void holdingEnterAfterTheNameDoesNotRunThroughToANewGame() throws Exception {
        router.startGame();
        TestApplication.playUntilGameOver(router);
        router.charTyped('A');

        router.keyPressed("ENTER"); // 이름 저장 → 순위표
        for (int i = 0; i < 10; i++) {
            router.keyPressed("ENTER"); // 떼지 않은 채 OS 자동 반복
        }

        assertInstanceOf(ScoreboardScreen.class, router.current());
        assertEquals(1, app.scores().list().size());

        router.keyReleased("ENTER");
        TestApplication.tap(router, "ENTER");
        assertInstanceOf(MenuScreen.class, router.current());
    }

    @Test
    void aKeyHeldAcrossAScreenChangeIsIgnoredOnlyUntilReleased() {
        router.keyPressed("ENTER"); // 메뉴 → 게임 시작
        assertInstanceOf(GameScreen.class, router.current());
        router.showMenu();

        router.keyPressed("ENTER");
        assertInstanceOf(MenuScreen.class, router.current());

        router.keyReleased("ENTER");
        router.keyPressed("ENTER");
        assertInstanceOf(GameScreen.class, router.current());
    }

    @Test
    void losingFocusForgetsHeldKeys() {
        router.keyPressed("ENTER");
        router.showMenu();

        router.focusLost(); // 다른 창에서 뗀 키는 전달되지 않는다
        router.keyPressed("ENTER");

        assertInstanceOf(GameScreen.class, router.current());
    }

    @Test
    void onlyNameEntryTurnsTheInputMethodOn() {
        assertFalse(router.acceptsTextInput());
        router.startGame();
        assertFalse(router.acceptsTextInput());

        TestApplication.playUntilGameOver(router);

        assertInstanceOf(NameEntryScreen.class, router.current());
        assertTrue(router.acceptsTextInput());
    }

    @Test
    void savedSettingsAreAppliedAndReported() throws Exception {
        Settings large = new Settings(Settings.ScreenSize.LARGE, Settings.defaults().keyBindings(), true);

        router.updateSettings(large);

        assertEquals(large, router.settings());
        assertEquals(large, app.applied());
        assertTrue(router.palette().colorBlind());
    }
}
