package team.tetris.ui.screen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import team.tetris.application.GameResult;
import team.tetris.application.GameStatus;
import team.tetris.application.model.ScoreStore;
import team.tetris.application.port.ScoreRepository;
import team.tetris.application.port.StorageException;
import team.tetris.core.TetrominoType;
import team.tetris.storage.memory.InMemorySettingsRepository;
import team.tetris.ui.ScreenRouter;
import team.tetris.ui.TestApplication;

class ScoreboardScreenTest {

    private final TestApplication app = new TestApplication().withPieces(TetrominoType.O);
    private final ScreenRouter router = app.router();

    /** 키를 한 번 눌렀다 뗀다. */
    private void tap(String key) {
        TestApplication.tap(router, key);
    }

    private void record(String name, long score) throws Exception {
        app.scores().register(new GameResult(UUID.randomUUID(), score, 0, 0, GameStatus.GAME_OVER), name);
    }

    @Test
    void emptyScoreboardSaysSo() {
        router.showScoreboard();

        assertTrue(router.render().text().contains("아직 기록이 없습니다"));
    }

    @Test
    void listsRecordsFromHighestScore() throws Exception {
        record("LOW", 10);
        record("HIGH", 300);
        record("MID", 50);

        router.showScoreboard();
        ScoreboardScreen board = (ScoreboardScreen) router.current();

        assertEquals(List.of("HIGH", "MID", "LOW"), board.entries().stream().map(e -> e.name()).toList());
        String text = router.render().text();
        assertTrue(text.indexOf("HIGH") < text.indexOf("MID"));
        assertTrue(text.indexOf("MID") < text.indexOf("LOW"));
        assertFalse(board.isAfterGame());
    }

    @Test
    void fromTheMenuBothEnterAndEscapeGoBack() {
        router.showScoreboard();
        tap("ESCAPE");
        assertInstanceOf(MenuScreen.class, router.current());

        router.showScoreboard();
        tap("ENTER");
        assertInstanceOf(MenuScreen.class, router.current());
        assertEquals(0, app.exits());
    }

    @Test
    void afterAGameEnterGoesToTheMenuAndEscapeExits() {
        router.startGame();
        TestApplication.playUntilGameOver(router);
        router.charTyped('Z');
        tap("ENTER");
        assertTrue(router.render().text().contains("이번 점수"));

        tap("ESCAPE");

        assertEquals(1, app.exits());
    }

    @Test
    void unknownKeysShowTheUsableKeys() {
        router.showScoreboard();
        tap("Q");
        tap("R");

        assertTrue(router.render().text().contains("사용할 수 있는 키"));
    }

    @Test
    void showsAnErrorWhenRecordsCannotBeRead() {
        ScoreRepository broken = new ScoreRepository() {
            @Override
            public ScoreStore load() throws StorageException {
                throw new StorageException(StorageException.Kind.READ_FAILED, Path.of("scores.bin"), null);
            }

            @Override
            public void save(ScoreStore store) {
            }
        };
        ScreenRouter brokenRouter = new TestApplication(new InMemorySettingsRepository(), broken).router();

        brokenRouter.showScoreboard();

        assertTrue(brokenRouter.render().text().contains("기록을 불러오지 못했습니다"));
    }
}
