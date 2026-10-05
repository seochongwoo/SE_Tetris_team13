package team.tetris.ui.screen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import team.tetris.application.GameCommand;
import team.tetris.application.GameResult;
import team.tetris.application.GameStatus;
import team.tetris.application.model.Settings;
import team.tetris.application.model.Settings.ScreenSize;
import team.tetris.application.port.SettingsRepository;
import team.tetris.application.port.StorageException;
import team.tetris.storage.memory.InMemoryScoreRepository;
import team.tetris.ui.ScreenRouter;
import team.tetris.ui.TestApplication;

class SettingsScreenTest {

    private static final int FIRST_KEY_ROW = 2;
    private static final int CLEAR_ROW = 10;
    private static final int RESET_ROW = 11;
    private static final int BACK_ROW = 12;

    private final TestApplication app = new TestApplication();
    private final ScreenRouter router = app.router();

    /** 키를 한 번 눌렀다 뗀다. */
    private void tap(String key) {
        TestApplication.tap(router, key);
    }
    private SettingsScreen screen;

    @BeforeEach
    void open() {
        router.showSettings();
        screen = (SettingsScreen) router.current();
    }

    private void moveTo(int row) {
        while (screen.cursor() != row) {
            tap("DOWN");
        }
    }

    private Settings saved() throws Exception {
        return app.settings().get();
    }

    @Test
    void arrowKeysChangeTheScreenSizeAndSaveIt() throws Exception {
        tap("RIGHT");
        assertEquals(ScreenSize.LARGE, saved().screenSize());
        assertEquals(ScreenSize.LARGE, app.applied().screenSize());
        assertTrue(screen.message().contains("저장했습니다"));

        tap("LEFT");
        tap("LEFT");
        assertEquals(ScreenSize.SMALL, saved().screenSize());
        assertTrue(router.render().text().contains("◄ 작게 ►"));
    }

    @Test
    void enterTogglesColorBlindModeAndSwitchesThePalette() throws Exception {
        moveTo(1);
        tap("ENTER");

        assertTrue(saved().colorBlindMode());
        assertTrue(router.palette().colorBlind());
        assertTrue(router.render().text().contains("켜짐"));
    }

    @Test
    void selectingAKeyRowThenPressingAKeyRebindsIt() throws Exception {
        moveTo(FIRST_KEY_ROW);
        tap("ENTER");
        assertTrue(screen.isWaitingForKey());
        assertTrue(router.render().text().contains("키를 누르세요"));

        tap("A");

        assertFalse(screen.isWaitingForKey());
        assertEquals("A", saved().keyBindings().get(GameCommand.MOVE_LEFT));
    }

    @Test
    void holdingTheEnterThatOpenedTheKeyPromptDoesNotBindEnter() throws Exception {
        moveTo(FIRST_KEY_ROW);
        router.keyPressed("ENTER");
        router.keyPressed("ENTER"); // 떼지 않은 채 OS 자동 반복

        assertTrue(screen.isWaitingForKey());
        assertEquals("LEFT", saved().keyBindings().get(GameCommand.MOVE_LEFT));

        router.keyReleased("ENTER");
        tap("ENTER"); // 떼었다가 다시 누르면 Enter도 의도적으로 지정할 수 있다

        assertFalse(screen.isWaitingForKey());
        assertEquals("ENTER", saved().keyBindings().get(GameCommand.MOVE_LEFT));
    }

    @Test
    void holdingEnterOnClearDoesNotSkipTheConfirmation() throws Exception {
        app.scores().register(new GameResult(UUID.randomUUID(), 100, 0, 0, GameStatus.GAME_OVER), "KEEP");
        moveTo(CLEAR_ROW);

        router.keyPressed("ENTER");
        router.keyPressed("ENTER");

        assertEquals(1, app.scores().list().size());
        assertTrue(router.render().text().contains("초기화할까요?"));
    }

    @Test
    void conflictingKeyIsRejectedAndNothingIsSaved() throws Exception {
        moveTo(FIRST_KEY_ROW + 1);
        tap("ENTER");

        tap("LEFT");

        assertEquals("RIGHT", saved().keyBindings().get(GameCommand.MOVE_RIGHT));
        assertTrue(screen.message().contains("겹치는"));
    }

    @Test
    void backspaceCancelsWaitingForAKey() throws Exception {
        moveTo(FIRST_KEY_ROW);
        tap("ENTER");

        tap("BACK_SPACE");

        assertFalse(screen.isWaitingForKey());
        assertEquals(Settings.defaults(), saved());
    }

    @Test
    void clearingTheScoreboardNeedsConfirmation() throws Exception {
        app.scores().register(new GameResult(UUID.randomUUID(), 100, 0, 0, GameStatus.GAME_OVER), "KEEP");
        moveTo(CLEAR_ROW);

        tap("ENTER");
        assertTrue(router.render().text().contains("초기화할까요?"));
        tap("ESCAPE");
        assertEquals(1, app.scores().list().size());

        tap("ENTER");
        tap("ENTER");
        assertTrue(app.scores().list().isEmpty());
    }

    @Test
    void resetRestoresTheDefaultSettings() throws Exception {
        tap("RIGHT");
        moveTo(RESET_ROW);

        tap("ENTER");
        tap("ENTER");

        assertEquals(Settings.defaults(), saved());
        assertEquals(Settings.defaults(), router.settings());
    }

    @Test
    void escapeAndTheBackRowReturnToTheMenu() {
        tap("ESCAPE");
        assertInstanceOf(MenuScreen.class, router.current());

        router.showSettings();
        screen = (SettingsScreen) router.current();
        moveTo(BACK_ROW);
        tap("ENTER");
        assertInstanceOf(MenuScreen.class, router.current());
    }

    @Test
    void unknownKeysExplainWhichKeysWork() {
        tap("Q");

        assertTrue(screen.message().contains("사용할 수 있는 키"));
    }

    @Test
    void failedSaveKeepsTheCurrentSettingsAndSaysSo() {
        SettingsRepository readOnly = new SettingsRepository() {
            @Override
            public Optional<Settings> load() {
                return Optional.empty();
            }

            @Override
            public void save(Settings settings) throws StorageException {
                throw new StorageException(StorageException.Kind.WRITE_FAILED, Path.of("settings.properties"), null);
            }
        };
        TestApplication brokenApp = new TestApplication(readOnly, new InMemoryScoreRepository());
        ScreenRouter brokenRouter = brokenApp.router();
        brokenRouter.showSettings();

        brokenRouter.keyPressed("RIGHT");

        assertEquals(ScreenSize.MEDIUM, brokenRouter.settings().screenSize());
        assertNull(brokenApp.applied());
        assertTrue(((SettingsScreen) brokenRouter.current()).message().contains("저장하지 못했습니다"));
    }
}
