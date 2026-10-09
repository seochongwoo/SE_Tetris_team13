package team.tetris.ui.screen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import team.tetris.application.GameResult;
import team.tetris.application.GameStatus;
import team.tetris.application.model.ScoreStore;
import team.tetris.application.model.GameMode;
import team.tetris.application.model.Difficulty;
import team.tetris.application.model.Settings;
import team.tetris.application.port.ScoreRepository;
import team.tetris.application.port.StorageException;
import team.tetris.core.TetrominoType;
import team.tetris.storage.memory.InMemorySettingsRepository;
import team.tetris.storage.memory.InMemoryScoreRepository;
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
    void filtersAllSixCategoriesAndStartsAtConfiguredDifficulty() throws Exception {
        for (GameMode mode : GameMode.values()) {
            for (Difficulty difficulty : Difficulty.values()) {
                app.scores().register(new GameResult(UUID.randomUUID(), 100, 0, 0, GameStatus.GAME_OVER,
                        mode, difficulty), "player");
            }
        }
        router.updateSettings(new Settings(Settings.ScreenSize.MEDIUM, Settings.defaults().keyBindings(), false,
                Difficulty.HARD));
        router.showScoreboard();
        var screen = (ScoreboardScreen) router.current();
        assertEquals(Difficulty.HARD, screen.entries().getFirst().difficulty());
        for (GameMode mode : GameMode.values()) {
            for (Difficulty difficulty : Difficulty.values()) {
                tap("RIGHT");
                assertEquals(1, screen.entries().size());
                assertEquals(mode, screen.entries().getFirst().mode());
                assertEquals(difficulty, screen.entries().getFirst().difficulty());
                assertTrue(router.render().text().contains(difficulty.name()));
                assertTrue(router.render().text().contains(mode == GameMode.NORMAL ? "일반" : "아이템"));
            }
            tap("DOWN");
        }
        tap("LEFT");
        assertEquals(Difficulty.NORMAL, screen.entries().getFirst().difficulty());
        tap("UP");
        assertEquals(GameMode.ITEM, screen.entries().getFirst().mode());
        assertEquals(Difficulty.HARD, router.settings().difficulty());
    }

    @Test
    void endingUsesFinishedDifficultyAndKeepsHighlightWhenSwitchingBack() throws Exception {
        record("other", 1000);
        var result = new GameResult(UUID.randomUUID(), 10, 0, 0, GameStatus.GAME_OVER,
                GameMode.ITEM, Difficulty.EASY);
        router.finishGame(result);
        router.charTyped('X');
        tap("ENTER");
        var screen = (ScoreboardScreen) router.current();
        UUID id = screen.highlightedRecordId().orElseThrow();
        assertEquals(id, screen.entries().getFirst().recordId());
        assertTrue(router.render().text().contains("아이템 / EASY"));
        tap("RIGHT");
        assertTrue(screen.entries().isEmpty());
        tap("LEFT");
        assertEquals(id, screen.entries().getFirst().recordId());
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
    void failedCategoryReadCanRetryWithoutChangingSettingsOrWritingScores() throws Exception {
        var backing = new InMemoryScoreRepository();
        boolean[] fail = {false};
        ScoreRepository repository = new ScoreRepository() {
            public ScoreStore load() throws StorageException {
                if (fail[0]) throw new StorageException(StorageException.Kind.READ_FAILED, Path.of("scores.bin"), null);
                return backing.load();
            }
            public void save(ScoreStore store) { backing.save(store); }
        };
        var application = new TestApplication(new InMemorySettingsRepository(), repository);
        var result = new GameResult(UUID.randomUUID(), 50, 0, 0, GameStatus.GAME_OVER,
                GameMode.NORMAL, Difficulty.HARD);
        application.scores().register(result, "hard");
        var screenRouter = application.router();
        screenRouter.showScoreboard();
        fail[0] = true;
        TestApplication.tap(screenRouter, "RIGHT");
        assertTrue(screenRouter.render().text().contains("기록을 불러오지 못했습니다"));
        assertTrue(((ScoreboardScreen) screenRouter.current()).entries().isEmpty());
        fail[0] = false;
        TestApplication.tap(screenRouter, "R");
        assertEquals("hard", ((ScoreboardScreen) screenRouter.current()).entries().getFirst().name());
        assertFalse(screenRouter.render().text().contains("기록을 불러오지 못했습니다"));
        assertEquals(Difficulty.NORMAL, screenRouter.settings().difficulty());
        assertEquals(1, backing.load().registrations().size());
    }

    @Test
    void endingRetryReadsAgainAndKeepsErrorUntilStorageRecovers() {
        var repository = new FailingScoreRepository();
        var application = new TestApplication(new InMemorySettingsRepository(), repository);
        var screenRouter = application.router();
        var result = new GameResult(UUID.randomUUID(), 50, 0, 0, GameStatus.GAME_OVER);
        screenRouter.finishGame(result);
        screenRouter.charTyped('X');
        TestApplication.tap(screenRouter, "ENTER");
        UUID savedId = ((ScoreboardScreen) screenRouter.current()).highlightedRecordId().orElseThrow();

        repository.failReads = true;
        TestApplication.tap(screenRouter, "RIGHT");
        TestApplication.tap(screenRouter, "LEFT");
        int readsBeforeRetry = repository.reads;
        TestApplication.tap(screenRouter, "R");
        assertEquals(readsBeforeRetry + 1, repository.reads);
        assertTrue(screenRouter.render().text().contains("기록을 불러오지 못했습니다"));
        assertTrue(((ScoreboardScreen) screenRouter.current()).entries().isEmpty());

        repository.failReads = false;
        TestApplication.tap(screenRouter, "R");
        var screen = (ScoreboardScreen) screenRouter.current();
        assertEquals(savedId, screen.entries().getFirst().recordId());
        assertEquals(savedId, screen.highlightedRecordId().orElseThrow());
        assertFalse(screenRouter.render().text().contains("기록을 불러오지 못했습니다"));
        assertEquals(1, repository.writes);
    }

    @ParameterizedTest
    @CsvSource({"R,R", "RIGHT,LEFT", "DOWN,UP"})
    void recoveringInitialScoreboardReadRestoresHighlightWithoutSavingAgain(String firstKey, String secondKey) {
        var repository = new FailingScoreRepository();
        repository.failAfterSave = true;
        var application = new TestApplication(new InMemorySettingsRepository(), repository);
        var screenRouter = application.router();
        var result = new GameResult(UUID.randomUUID(), 50, 0, 0, GameStatus.GAME_OVER,
                GameMode.ITEM, Difficulty.EASY);
        screenRouter.finishGame(result);
        screenRouter.charTyped('X');
        TestApplication.tap(screenRouter, "ENTER");
        assertTrue(screenRouter.render().text().contains("기록을 불러오지 못했습니다"));
        assertTrue(((ScoreboardScreen) screenRouter.current()).highlightedRecordId().isEmpty());

        repository.failReads = false;
        TestApplication.tap(screenRouter, firstKey);
        TestApplication.tap(screenRouter, secondKey);
        var screen = (ScoreboardScreen) screenRouter.current();
        assertEquals(1, screen.entries().size());
        var entry = screen.entries().getFirst();
        assertEquals(result.gameId(), entry.gameId());
        assertEquals(entry.recordId(), screen.highlightedRecordId().orElseThrow());
        assertFalse(screenRouter.render().text().contains("기록을 불러오지 못했습니다"));
        assertTrue(screenRouter.render().text().contains("아이템 / EASY"));
        assertEquals(1, repository.writes);
        assertEquals(1, repository.backing.load().registrations().size());
    }

    private static final class FailingScoreRepository implements ScoreRepository {
        private final InMemoryScoreRepository backing = new InMemoryScoreRepository();
        private boolean failReads;
        private boolean failAfterSave;
        private int reads;
        private int writes;

        @Override
        public ScoreStore load() throws StorageException {
            reads++;
            if (failReads) {
                throw new StorageException(StorageException.Kind.READ_FAILED, Path.of("scores.bin"), null);
            }
            return backing.load();
        }

        @Override
        public void save(ScoreStore store) {
            writes++;
            backing.save(store);
            if (failAfterSave) failReads = true;
        }
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
