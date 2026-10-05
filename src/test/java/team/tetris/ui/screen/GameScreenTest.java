package team.tetris.ui.screen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static team.tetris.ui.TestApplication.FRAME;

import java.nio.file.Path;
import java.util.Optional;
import org.junit.jupiter.api.Test;
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
