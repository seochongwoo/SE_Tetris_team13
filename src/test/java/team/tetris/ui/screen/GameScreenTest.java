package team.tetris.ui.screen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static team.tetris.ui.TestApplication.FRAME;

import java.nio.file.Path;
import java.util.EnumMap;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import team.tetris.application.GameCommand;
import team.tetris.application.GameStatus;
import team.tetris.application.model.Settings;
import team.tetris.application.port.SettingsRepository;
import team.tetris.application.port.StorageException;
import team.tetris.core.Position;
import team.tetris.core.TetrominoType;
import team.tetris.storage.memory.InMemoryScoreRepository;
import team.tetris.ui.ScreenRouter;
import team.tetris.ui.TestApplication;

class GameScreenTest {

    private final TestApplication app = new TestApplication().withPieces(TetrominoType.T);
    private final ScreenRouter router = app.router();

    private GameScreen start() {
        router.startGame();
        return (GameScreen) router.current();
    }

    private static Position origin(GameScreen game) {
        return game.session().snapshot().engine().activePiece().origin();
    }

    private static GameStatus status(GameScreen game) {
        return game.session().snapshot().status();
    }

    @Test
    void keyPressIsAppliedOnTheNextFrame() {
        GameScreen game = start();
        int x = origin(game).x();

        router.keyPressed("LEFT");
        assertEquals(x, origin(game).x());

        router.update(FRAME);
        assertEquals(x - 1, origin(game).x());
    }

    @Test
    void osAutoRepeatOfAHeldKeyIsIgnored() {
        GameScreen game = start();
        int x = origin(game).x();

        router.keyPressed("LEFT");
        router.keyPressed("LEFT");
        router.update(FRAME);

        assertEquals(x - 1, origin(game).x());
    }

    @Test
    void holdingAMovementKeyRepeatsUntilReleased() {
        GameScreen game = start();
        int x = origin(game).x();

        router.keyPressed("LEFT");
        for (int frame = 0; frame < 11; frame++) {
            router.update(FRAME); // 11 * 16ms = 176ms: 첫 반복(170ms)까지 지남
        }
        assertEquals(x - 2, origin(game).x());

        router.keyReleased("LEFT");
        for (int frame = 0; frame < 10; frame++) {
            router.update(FRAME);
        }
        assertEquals(x - 2, origin(game).x());
    }

    @Test
    void pauseKeyOpensThePauseMenu() {
        GameScreen game = start();

        router.keyPressed("P");
        router.update(FRAME);

        assertEquals(GameStatus.PAUSED, status(game));
        String text = router.render().text();
        assertTrue(text.contains("일시정지"));
        assertTrue(text.contains("재개"));
        assertTrue(text.contains("메뉴로"));
        assertTrue(text.contains("프로그램 종료"));
        assertTrue(text.contains("Enter 선택"));
        assertTrue(text.contains("↑/↓ 이동"));
    }

    @Test
    void escapeOpensThePauseMenuAndClosesItAgain() {
        GameScreen game = start();

        router.keyPressed("ESCAPE");
        router.keyReleased("ESCAPE");
        router.update(FRAME);
        assertEquals(GameStatus.PAUSED, status(game));

        router.keyPressed("ESCAPE");
        router.update(FRAME);
        assertEquals(GameStatus.RUNNING, status(game));
    }

    @ParameterizedTest
    @ValueSource(strings = {"P", "ESCAPE"})
    void pauseAndResumeRequireReleaseBeforeAnotherPress(String key) {
        GameScreen game = start();
        router.keyPressed(key);
        router.update(FRAME);
        assertEquals(GameStatus.PAUSED, status(game));

        router.keyPressed(key);
        router.update(FRAME);
        assertEquals(GameStatus.PAUSED, status(game));

        router.keyReleased(key);
        router.keyPressed(key);
        router.update(FRAME);
        assertEquals(GameStatus.RUNNING, status(game));

        router.keyPressed(key);
        router.update(FRAME);
        assertEquals(GameStatus.RUNNING, status(game));

        router.keyReleased(key);
        router.keyPressed(key);
        router.update(FRAME);
        assertEquals(GameStatus.PAUSED, status(game));
    }

    @ParameterizedTest
    @ValueSource(strings = {"UP", "DOWN", "ENTER"})
    void customResumeKeyTakesPriorityOverPauseMenuControls(String key) throws StorageException {
        Settings defaults = Settings.defaults();
        var bindings = new EnumMap<>(defaults.keyBindings());
        bindings.put(GameCommand.RESUME, key);
        app.settings().update(new Settings(defaults.screenSize(), bindings, defaults.colorBlindMode()));
        GameScreen game = start();
        router.keyPressed("P");
        router.keyReleased("P");
        router.update(FRAME);

        // Enter도 현재 선택 항목(프로그램 종료) 대신 사용자 지정 재개를 실행해야 한다.
        if (key.equals("ENTER")) {
            router.keyPressed("UP");
            router.keyReleased("UP");
        }
        int cursor = game.pauseCursor();
        router.keyPressed(key);
        assertEquals(cursor, game.pauseCursor());
        router.update(FRAME);

        assertEquals(GameStatus.RUNNING, status(game));
        assertEquals(0, app.exits());
        assertEquals(game, router.current());
    }

    @ParameterizedTest
    @CsvSource({
            "ENTER, ESCAPE, SPACE, Space, DOWN",
            "ENTER, ESCAPE, SPACE, Space, UP",
            "P, ENTER, SPACE, Space, DOWN",
            "P, ENTER, SPACE, Space, UP",
            "ENTER, SPACE, F1, F1, DOWN",
            "ENTER, SPACE, F1, F1, UP",
            "SPACE, ENTER, F1, F1, DOWN",
            "SPACE, ENTER, F1, F1, UP"
    })
    void alternateSelectionKeyKeepsExitMenusAccessible(String resumeKey, String quitKey,
            String selectKey, String displayKey, String direction) throws StorageException {
        Settings defaults = Settings.defaults();
        var bindings = new EnumMap<>(defaults.keyBindings());
        bindings.put(GameCommand.RESUME, resumeKey);
        bindings.put(GameCommand.QUIT_GAME, quitKey);
        if (quitKey.equals("SPACE")) {
            bindings.put(GameCommand.HARD_DROP, "H");
        }
        app.settings().update(new Settings(defaults.screenSize(), bindings, defaults.colorBlindMode()));
        GameScreen game = start();
        router.keyPressed("P");
        router.keyReleased("P");
        router.update(FRAME);

        assertTrue(router.render().text().contains(displayKey + " 선택"));
        router.keyPressed(direction);
        router.keyReleased(direction);
        router.keyPressed(selectKey);
        router.keyReleased(selectKey);
        router.update(FRAME);

        if (direction.equals("DOWN")) {
            assertEquals(GameStatus.ABORTED, status(game));
            assertInstanceOf(MenuScreen.class, router.current());
            assertEquals(0, app.exits());
        } else {
            assertEquals(1, app.exits());
        }
    }

    @ParameterizedTest
    @CsvSource({
            "UP, DOWN, W, S, W/S, 1",
            "UP, DOWN, W, S, W/S, 2",
            "DOWN, UP, W, S, W/S, 1",
            "DOWN, UP, W, S, W/S, 2",
            "UP, W, F2, DOWN, F2/↓, 1",
            "UP, W, F2, DOWN, F2/↓, 2",
            "DOWN, S, UP, F3, ↑/F3, 1",
            "DOWN, S, UP, F3, ↑/F3, 2",
            "ENTER, UP, W, DOWN, W/↓, 1",
            "ENTER, UP, W, DOWN, W/↓, 2",
            "DOWN, ENTER, UP, S, ↑/S, 1",
            "DOWN, ENTER, UP, S, ↑/S, 2"
    })
    void alternateMovementKeysKeepBothExitItemsAccessible(String resumeKey, String quitKey,
            String upKey, String downKey, String hint, int target) throws StorageException {
        Settings defaults = Settings.defaults();
        var bindings = new EnumMap<>(defaults.keyBindings());
        bindings.put(GameCommand.ROTATE_CW, "R");
        bindings.put(GameCommand.SOFT_DROP, "D");
        bindings.put(GameCommand.RESUME, resumeKey);
        bindings.put(GameCommand.QUIT_GAME, quitKey);
        app.settings().update(new Settings(defaults.screenSize(), bindings, defaults.colorBlindMode()));
        GameScreen game = start();
        router.keyPressed("P");
        router.keyReleased("P");
        router.update(FRAME);
        assertTrue(router.render().text().contains(hint + " 이동"));

        // 양방향 이동과 누른 채 발생한 OS 반복 억제를 함께 확인한다.
        router.keyPressed(upKey);
        router.keyPressed(upKey);
        router.update(FRAME);
        assertEquals(GameStatus.PAUSED, status(game));
        assertEquals(2, game.pauseCursor());
        router.keyReleased(upKey);
        router.keyPressed(downKey);
        router.keyReleased(downKey);
        assertEquals(0, game.pauseCursor());
        for (int i = 0; i < target; i++) {
            router.keyPressed(downKey);
            router.keyReleased(downKey);
        }
        String selectKey = resumeKey.equals("ENTER") || quitKey.equals("ENTER") ? "SPACE" : "ENTER";
        router.keyPressed(selectKey);
        router.update(FRAME);

        if (target == 1) {
            assertEquals(GameStatus.ABORTED, status(game));
            assertInstanceOf(MenuScreen.class, router.current());
            assertEquals(0, app.exits());
        } else {
            assertEquals(1, app.exits());
        }
    }

    @Test
    void resumeItemContinuesTheGame() {
        GameScreen game = start();
        router.keyPressed("P");
        router.keyReleased("P");
        router.update(FRAME);

        router.keyPressed("DOWN");
        router.keyPressed("UP");
        assertEquals(0, game.pauseCursor());
        router.keyPressed("ENTER");
        router.update(FRAME);

        assertEquals(GameStatus.RUNNING, status(game));
    }

    @Test
    void menuItemAbortsTheGameAndReturnsToTheStartMenu() {
        start();
        router.keyPressed("P");
        router.update(FRAME);

        router.keyPressed("DOWN");
        router.keyPressed("ENTER");
        router.update(FRAME);

        assertInstanceOf(MenuScreen.class, router.current());
        assertEquals(0, app.exits());
    }

    @Test
    void exitItemClosesTheProgram() {
        start();
        router.keyPressed("P");
        router.update(FRAME);

        router.keyPressed("UP");
        router.keyPressed("ENTER");

        assertEquals(1, app.exits());
    }

    @Test
    void losingFocusPausesTheGame() {
        GameScreen game = start();

        router.focusLost();
        router.update(FRAME);

        assertEquals(GameStatus.PAUSED, status(game));
    }

    @Test
    void gameOverIsShownBrieflyThenMovesToNameEntry() {
        GameScreen game = start();
        for (int i = 0; i < 200 && game.session().result().isEmpty(); i++) {
            router.keyPressed("SPACE");
            router.keyReleased("SPACE");
            router.update(FRAME);
        }
        assertEquals(GameStatus.GAME_OVER, status(game));
        assertInstanceOf(GameScreen.class, router.current());
        assertTrue(router.render().text().contains("GAME OVER"));

        router.update(GameScreen.GAME_OVER_DELAY_NANOS);

        assertInstanceOf(NameEntryScreen.class, router.current());
    }

    @Test
    void enterSkipsTheGameOverDelay() {
        start();

        TestApplication.playUntilGameOver(router);

        assertInstanceOf(NameEntryScreen.class, router.current());
    }

    @Test
    void showsAWarningWhenSettingsFellBackToDefaults() {
        SettingsRepository broken = new SettingsRepository() {
            @Override
            public Optional<Settings> load() throws StorageException {
                throw new StorageException(StorageException.Kind.READ_FAILED, Path.of("settings.properties"), null);
            }

            @Override
            public void save(Settings settings) {
            }
        };
        ScreenRouter brokenRouter = new TestApplication(broken, new InMemoryScoreRepository()).router();

        brokenRouter.startGame();

        assertTrue(brokenRouter.render().text().contains("기본값으로 진행합니다"));
    }
}
