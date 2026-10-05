package team.tetris.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;
import team.tetris.application.model.Settings;
import team.tetris.core.TetrominoType;
import team.tetris.ui.screen.MenuScreen;
import team.tetris.ui.screen.NameEntryScreen;

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
    void savedSettingsAreAppliedAndReported() throws Exception {
        Settings large = new Settings(Settings.ScreenSize.LARGE, Settings.defaults().keyBindings(), true);

        router.updateSettings(large);

        assertEquals(large, router.settings());
        assertEquals(large, app.applied());
        assertTrue(router.palette().colorBlind());
    }
}
