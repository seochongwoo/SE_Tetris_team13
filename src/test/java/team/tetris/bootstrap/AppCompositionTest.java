package team.tetris.bootstrap;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import team.tetris.application.*;
import team.tetris.application.EndGameView.Stage;
import team.tetris.application.model.Settings;
import team.tetris.application.model.Difficulty;
import team.tetris.core.TetrominoType;
import team.tetris.core.rule.PieceGenerator;

class AppCompositionTest {
    @TempDir Path directory;

    private static PieceGenerator squares() {
        return new PieceGenerator() {
            public TetrominoType next() { return TetrominoType.O; }
            public TetrominoType peek() { return TetrominoType.O; }
        };
    }

    @Test
    void realEngineToNameToSavedScoreAndRecreatedApplication() throws Exception {
        var app = new AppComposition(directory, AppCompositionTest::squares, new ScorePolicy(), new SpeedPolicy());
        var changed = new Settings(Settings.ScreenSize.LARGE, Settings.defaults().keyBindings(), true);
        app.settings().update(changed);
        StartedGame started = app.newGame();
        assertEquals(changed, started.settings().value());
        GameSession session = started.session();
        assertEquals(20, session.snapshot().engine().board().length);
        assertEquals(10, session.snapshot().engine().board()[0].length);
        session.handle(GameCommand.PAUSE);
        session.update(Long.MAX_VALUE);
        assertEquals(0, session.snapshot().score());
        session.handle(GameCommand.RESUME);
        for (int i = 0; i < 20 && session.result().isEmpty(); i++) session.handle(GameCommand.HARD_DROP);
        GameResult result = session.result().orElseThrow();
        assertEquals(90, result.score());
        assertEquals(Stage.NAME_REQUIRED, app.endings().begin(result).stage());
        EndGameView saved = app.endings().submitName(result.gameId(), "플레이어");
        assertEquals(Stage.SHOW_SCOREBOARD, saved.stage());
        var reopened = new AppComposition(directory);
        assertEquals(changed, reopened.settings().get());
        EndGameView restored = reopened.endings().begin(result);
        assertEquals(saved.highlightedRecordId(), restored.highlightedRecordId());
        assertEquals(saved.scores(), restored.scores());
        assertEquals(1, reopened.scores().list().size());
    }

    @Test
    void eachNewGameCreatesIndependentEngineAndReloadsSettings() throws Exception {
        AtomicInteger generators = new AtomicInteger();
        var app = new AppComposition(directory, () -> { generators.incrementAndGet(); return squares(); },
                (distance, lines, level) -> distance * 5L, new SpeedPolicy());
        StartedGame first = app.newGame();
        first.session().update(1_000_000_000L);
        assertEquals(5, first.session().snapshot().score());
        var changed = new Settings(Settings.ScreenSize.SMALL, Settings.defaults().keyBindings(), true);
        app.settings().update(changed);
        StartedGame second = app.newGame();
        assertEquals(0, second.session().snapshot().score());
        assertEquals(Settings.defaults(), first.settings().value());
        assertEquals(changed, second.settings().value());
        first.session().handle(GameCommand.QUIT_GAME);
        second.session().handle(GameCommand.QUIT_GAME);
        assertNotEquals(first.session().result().orElseThrow().gameId(), second.session().result().orElseThrow().gameId());
        assertEquals(2, generators.get());
    }

    @Test
    void difficultyIsAppliedAtGameStartAndChangesOnlyTheNextGame() throws Exception {
        var app = new AppComposition(directory, AppCompositionTest::squares, new ScorePolicy(), SpeedPolicy::new);
        app.settings().update(new Settings(Settings.ScreenSize.MEDIUM, Settings.defaults().keyBindings(), false,
                Difficulty.EASY));
        GameSession easy = app.newGame().session();
        app.settings().update(new Settings(Settings.ScreenSize.MEDIUM, Settings.defaults().keyBindings(), false,
                Difficulty.HARD));
        GameSession hard = app.newGame().session();
        for (GameSession session : new GameSession[]{easy, hard}) {
            // 25개의 O 블록으로 10줄을 삭제해 첫 가속을 실제 엔진에서 확인한다.
            for (int pair = 0; pair < 5; pair++) {
                for (int target : new int[]{0, 2, 4, 6, 8}) {
                    for (int i = 0; i < 10; i++) session.handle(GameCommand.MOVE_LEFT);
                    for (int i = 0; i < target; i++) session.handle(GameCommand.MOVE_RIGHT);
                    session.handle(GameCommand.HARD_DROP);
                }
            }
            assertEquals(10, session.snapshot().clearedLines());
            assertEquals(1, session.snapshot().level());
        }
        assertEquals(920_000_000L, easy.snapshot().gravityIntervalNanos());
        assertEquals(880_000_000L, hard.snapshot().gravityIntervalNanos());
        var before = hard.snapshot().engine().activePiece().origin();
        hard.update(879_999_999L);
        assertEquals(before, hard.snapshot().engine().activePiece().origin());
        hard.update(1);
        assertEquals(before.translate(0, 1), hard.snapshot().engine().activePiece().origin());
    }

    @Test
    void corruptSettingsAreReportedWithFallbackWithoutOverwritingOriginal() throws Exception {
        Path file = directory.resolve("settings.properties");
        Files.writeString(file, "corrupt");
        StartedGame game = new AppComposition(directory).newGame();
        assertEquals(Settings.defaults(), game.settings().value());
        assertTrue(game.settings().error().isPresent());
        assertEquals("corrupt", Files.readString(file));
    }

    @Test
    void injectedFakeUiExercisesApplicationLaunchAndEnding() {
        AtomicInteger opened = new AtomicInteger();
        TetrisApplication.launch(directory, app -> {
            opened.incrementAndGet();
            GameSession session = app.newGame().session();
            for (int i = 0; i < 1000 && session.result().isEmpty(); i++) session.handle(GameCommand.HARD_DROP);
            GameResult result = session.result().orElseThrow();
            assertEquals(Stage.NAME_REQUIRED, app.endings().begin(result).stage());
            EndGameView saved = app.endings().submitName(result.gameId(), "test");
            assertEquals(Stage.SHOW_SCOREBOARD, saved.stage());
            assertTrue(saved.highlightedRecordId().isPresent());
            GameSession next = app.newGame().session();
            next.handle(GameCommand.QUIT_GAME);
            assertEquals(Stage.RETURN_MENU, app.endings().begin(next.result().orElseThrow()).stage());
        });
        assertEquals(1, opened.get());
        assertTrue(Files.exists(directory.resolve("scores.bin")));
    }

    @Test
    void missingProductionUiFailsClearlyWithoutCreatingDataDirectory() throws Exception {
        Path data = directory.resolve("unused data");
        var classes = TetrisApplication.class.getProtectionDomain().getCodeSource().getLocation();
        ClassLoader previous = Thread.currentThread().getContextClassLoader();
        try (var isolated = new java.net.URLClassLoader(new java.net.URL[]{classes},
                ClassLoader.getPlatformClassLoader())) {
            Thread.currentThread().setContextClassLoader(isolated);
            var entry = isolated.loadClass(TetrisApplication.class.getName())
                    .getMethod("main", String[].class);
            var failure = assertThrows(java.lang.reflect.InvocationTargetException.class,
                    () -> entry.invoke(null, (Object) new String[]{"--data-dir", data.toString()}));
            assertInstanceOf(IllegalStateException.class, failure.getCause());
            assertTrue(failure.getCause().getMessage().contains("No GameUi provider installed"));
            assertFalse(Files.exists(data));
        } finally {
            Thread.currentThread().setContextClassLoader(previous);
        }
    }

    @Test
    void entryPointLoadsUiProviderAndHonorsExplicitDataDirectory() {
        TetrisApplication.main(new String[]{"--data-dir", directory.toString()});
        assertNotNull(TestGameUi.application);
        assertEquals(GameStatus.RUNNING, TestGameUi.application.newGame().session().snapshot().status());
        assertEquals(directory.resolve(".se-tetris-team13"), TetrisApplication.dataDirectory(new String[0], directory));
        assertEquals(directory, TetrisApplication.dataDirectory(new String[]{"--data-dir", directory.toString()}, directory));
        for (String[] invalid : new String[][] {{"unknown"}, {"--data-dir"}, {"--data-dir", " "}}) {
            assertThrows(IllegalArgumentException.class, () -> TetrisApplication.dataDirectory(invalid, directory));
        }
    }
}
