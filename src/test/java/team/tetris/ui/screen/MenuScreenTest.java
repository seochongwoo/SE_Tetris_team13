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
        for (String label : List.of("게임 시작", "설정", "스코어보드", "종료")) {
            assertTrue(text.contains(label), label);
        }
        assertTrue(text.contains("↑↓ 이동   Enter 선택"));
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
        assertInstanceOf(SettingsScreen.class, router.current());

        router.showMenu();
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
}
