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
import java.util.EnumMap;
import team.tetris.application.model.BattleKeyBindings;
import team.tetris.application.model.Settings;
import team.tetris.application.model.Difficulty;
import team.tetris.application.model.Settings.ScreenSize;
import team.tetris.application.port.SettingsRepository;
import team.tetris.application.port.StorageException;
import team.tetris.storage.memory.InMemoryScoreRepository;
import team.tetris.ui.ScreenRouter;
import team.tetris.ui.TestApplication;

class SettingsScreenTest {

    private static final int DIFFICULTY_ROW = 0;
    private static final int SIZE_ROW = 1;
    private static final int COLOR_ROW = 2;
    private static final int KEYS_ROW = 3;
    private static final int BATTLE_KEYS_ROW = 4;
    private static final int CLEAR_ROW = 5;
    private static final int RESET_ROW = 6;
    private static final int BACK_ROW = 7;
    /** 조작 키 페이지 안의 줄. */
    private static final int FIRST_KEY_ROW = 0;
    private static final int KEY_PAGE_BACK_ROW = 8;

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

    private void openKeyPage() {
        moveTo(KEYS_ROW);
        tap("ENTER");
        assertTrue(screen.isEditingKeys());
    }

    /** 대전 키 페이지 안의 줄: 조작 다섯 줄(회전, ←, →, ↓, 떨어뜨리기), 기본 키로 되돌리기, 돌아가기. */
    private static final int BATTLE_DROP_ROW = 4;
    private static final int BATTLE_RESET_ROW = 5;
    private static final int BATTLE_BACK_ROW = 6;

    private void openBattlePage() {
        moveTo(BATTLE_KEYS_ROW);
        tap("ENTER");
        assertTrue(screen.isEditingBattleKeys());
    }

    private Settings saved() throws Exception {
        return app.settings().get();
    }

    @Test
    void difficultyIsTheFirstRowAndShowsEveryChoiceInKorean() {
        assertEquals(DIFFICULTY_ROW, screen.cursor());
        String text = router.render().text();

        assertTrue(text.contains("[보통]"), text);
        assertTrue(text.contains("쉬움") && text.contains("어려움"));
        assertFalse(text.contains("NORMAL"));
        assertTrue(text.contains("모든 블록이 같은 확률로"));
    }

    @Test
    void arrowsMoveToTheNeighbourChoiceAndStopAtTheEnds() throws Exception {
        tap("LEFT");
        assertEquals(Difficulty.EASY, saved().difficulty());
        assertTrue(screen.message().contains("난이도 쉬움"));
        assertTrue(router.render().text().contains("I 블록이 더 자주"));

        tap("LEFT");
        assertEquals(Difficulty.EASY, saved().difficulty());

        tap("RIGHT");
        tap("RIGHT");
        assertEquals(Difficulty.HARD, saved().difficulty());
        assertTrue(router.render().text().contains("[어려움]"));
        tap("RIGHT");
        assertEquals(Difficulty.HARD, saved().difficulty());
    }

    @Test
    void difficultyCyclesWithEnterAndSurvivesOtherSettingsEditsAndReset() throws Exception {
        tap("RIGHT");
        assertEquals(Difficulty.HARD, saved().difficulty());
        moveTo(SIZE_ROW);
        tap("RIGHT");
        moveTo(COLOR_ROW);
        tap("ENTER");
        openKeyPage();
        moveTo(FIRST_KEY_ROW);
        tap("ENTER");
        tap("A");
        tap("ESCAPE");
        assertFalse(screen.isEditingKeys());
        assertEquals(Difficulty.HARD, saved().difficulty());
        moveTo(DIFFICULTY_ROW);
        tap("ENTER");
        assertEquals(Difficulty.EASY, saved().difficulty());
        moveTo(RESET_ROW);
        tap("ENTER");
        tap("ENTER");
        assertEquals(Difficulty.NORMAL, saved().difficulty());
    }

    @Test
    void arrowKeysChangeTheScreenSizeAndSaveIt() throws Exception {
        moveTo(SIZE_ROW);
        tap("RIGHT");
        assertEquals(ScreenSize.LARGE, saved().screenSize());
        assertEquals(ScreenSize.LARGE, app.applied().screenSize());
        assertTrue(screen.message().contains("저장했습니다"));

        tap("LEFT");
        tap("LEFT");
        assertEquals(ScreenSize.SMALL, saved().screenSize());
        assertTrue(router.render().text().contains("[작게]"));
    }

    @Test
    void enterTogglesColorBlindModeAndSwitchesThePalette() throws Exception {
        moveTo(COLOR_ROW);
        tap("ENTER");

        assertTrue(saved().colorBlindMode());
        assertTrue(router.palette().colorBlind());
        assertTrue(router.render().text().contains("[켜짐]"));
    }

    @Test
    void selectingAKeyRowThenPressingAKeyRebindsIt() throws Exception {
        openKeyPage();
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
        openKeyPage();
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
        openKeyPage();
        moveTo(FIRST_KEY_ROW + 1);
        tap("ENTER");

        tap("LEFT");

        assertEquals("RIGHT", saved().keyBindings().get(GameCommand.MOVE_RIGHT));
        assertTrue(screen.message().contains("겹칩"));
    }

    @Test
    void backspaceCancelsWaitingForAKey() throws Exception {
        openKeyPage();
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
        moveTo(SIZE_ROW);
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

        assertEquals(Difficulty.NORMAL, brokenRouter.settings().difficulty());
        TestApplication.tap(brokenRouter, "DOWN");
        TestApplication.tap(brokenRouter, "LEFT");
        assertEquals(ScreenSize.MEDIUM, brokenRouter.settings().screenSize());
        assertNull(brokenApp.applied());
        assertTrue(((SettingsScreen) brokenRouter.current()).message().contains("저장하지 못했습니다"));
    }

    @Test
    void keyPageListsTheEightControlsAndReturnsWithBackOrEscape() {
        openKeyPage();
        String text = router.render().text();
        assertTrue(text.contains("조작 키 설정"));
        assertTrue(text.contains("한 번에 떨어뜨리기") && text.contains("게임 메뉴"));

        moveTo(KEY_PAGE_BACK_ROW);
        tap("ENTER");
        assertFalse(screen.isEditingKeys());
        assertEquals(KEYS_ROW, screen.cursor());

        tap("ENTER");
        tap("ESCAPE");
        assertFalse(screen.isEditingKeys());
        assertInstanceOf(SettingsScreen.class, router.current());

        tap("ENTER");
        tap("Q");
        assertTrue(screen.message().contains("사용할 수 있는 키"));
    }

    @Test
    void mainListHasEightRowsAndKeyRowsMovedOut() {
        String text = router.render().text();

        assertTrue(text.contains("조작 키 설정"));
        assertFalse(text.contains("한 번에 떨어뜨리기"));
        moveTo(BACK_ROW);
        tap("DOWN");
        assertEquals(DIFFICULTY_ROW, screen.cursor());
    }

    @Test
    void battlePageShowsBothPlayersAndRebindsTheChosenPlayersKey() throws Exception {
        openBattlePage();
        String text = router.render().text();
        assertTrue(text.contains("대전 조작 키") && text.contains("1P") && text.contains("2P"), text);
        assertTrue(text.contains("Space") && text.contains("Enter") && text.contains("두 사람 공통"), text);
        assertFalse(text.contains("대체 키"), "기본 설정에서는 대체 키 안내가 없다");

        tap("RIGHT");
        assertEquals(2, screen.battlePlayer());
        moveTo(BATTLE_DROP_ROW);
        tap("ENTER");
        assertTrue(router.render().text().contains("'2P 한 번에 떨어뜨리기'에 쓸 키를 누르세요"));
        tap("SHIFT");

        assertEquals("SHIFT", saved().battleKeys().two().get(GameCommand.HARD_DROP));
        assertEquals(BattleKeyBindings.standard().one(), saved().battleKeys().one());
        assertTrue(screen.message().contains("2P"));
        assertEquals("SHIFT", app.applied().battleKeys().two().get(GameCommand.HARD_DROP));
    }

    @Test
    void battleKeysCannotClashWithTheOtherPlayerOrTheCommonKeys() throws Exception {
        openBattlePage();
        tap("RIGHT");
        tap("ENTER"); // 2P 회전
        tap("W");     // 1P 회전 키

        assertEquals("UP", saved().battleKeys().two().get(GameCommand.ROTATE_CW));
        assertEquals("W: 1P '회전'과 겹칩니다", screen.message());

        tap("LEFT");
        moveTo(BATTLE_DROP_ROW);
        tap("ENTER");
        tap("P");     // 공통 일시정지 키

        assertEquals("SPACE", saved().battleKeys().one().get(GameCommand.HARD_DROP));
        assertEquals("P: 공통 '일시정지'와 겹칩니다", screen.message());
    }

    @Test
    void aCommonKeyCannotTakeABattleKey() throws Exception {
        openKeyPage();
        moveTo(GameCommand.PAUSE.ordinal());
        tap("ENTER");
        tap("W");

        assertEquals("P", saved().keyBindings().get(GameCommand.PAUSE));
        assertEquals("W: 1P '회전'과 겹칩니다", screen.message());

        // 1인 게임 조작 키는 대전 키와 같아도 된다.
        moveTo(GameCommand.HARD_DROP.ordinal());
        tap("ENTER");
        tap("W");
        assertEquals("W", saved().keyBindings().get(GameCommand.HARD_DROP));
    }

    @Test
    void holdingTheEnterThatOpenedTheBattleKeyPromptDoesNotBindEnter() throws Exception {
        openBattlePage();
        router.keyPressed("ENTER");
        router.keyPressed("ENTER"); // 떼지 않은 채 OS 자동 반복

        assertTrue(screen.isWaitingForKey());
        assertEquals("W", saved().battleKeys().one().get(GameCommand.ROTATE_CW));

        router.keyReleased("ENTER");
        tap("BACK_SPACE");
        assertFalse(screen.isWaitingForKey());
        assertEquals("W", saved().battleKeys().one().get(GameCommand.ROTATE_CW));
    }

    @Test
    void resetRowRestoresOnlyTheBattleKeysAndBackOrEscapeLeave() throws Exception {
        Settings changed = Settings.defaults().withDifficulty(Difficulty.HARD)
                .withBattleKeys(Settings.defaults().battleKeys().with(1, GameCommand.HARD_DROP, "F"));
        router.updateSettings(changed);
        openBattlePage();
        moveTo(BATTLE_RESET_ROW);
        tap("ENTER");

        assertEquals(BattleKeyBindings.standard(), saved().battleKeys());
        assertEquals(Difficulty.HARD, saved().difficulty());

        moveTo(BATTLE_BACK_ROW);
        tap("ENTER");
        assertFalse(screen.isEditingBattleKeys());
        assertEquals(BATTLE_KEYS_ROW, screen.cursor());
        tap("ENTER");
        tap("ESCAPE");
        assertFalse(screen.isEditingBattleKeys());
        assertInstanceOf(SettingsScreen.class, router.current());
    }

    @Test
    void usersWhoseCommonKeysHitTheDefaultsSeeTheReplacementAndResetKeepsIt() throws Exception {
        var keys = new EnumMap<>(Settings.defaults().keyBindings());
        keys.put(GameCommand.PAUSE, "S");
        keys.put(GameCommand.RESUME, "S");
        router.updateSettings(new Settings(ScreenSize.MEDIUM, keys, false));
        openBattlePage();

        String text = router.render().text();
        assertTrue(text.contains("대체 키"), text);
        assertEquals("Q", saved().battleKeys().one().get(GameCommand.SOFT_DROP));

        moveTo(BATTLE_RESET_ROW);
        tap("ENTER");
        assertEquals("Q", saved().battleKeys().one().get(GameCommand.SOFT_DROP));
        assertEquals("S", saved().keyBindings().get(GameCommand.PAUSE));
    }

    @Test
    void longKeyNamesOnTheBattlePageStayInTheirColumn() throws Exception {
        router.updateSettings(Settings.defaults().withBattleKeys(Settings.defaults().battleKeys()
                .with(1, GameCommand.ROTATE_CW, "CLOSE_BRACKET").with(2, GameCommand.ROTATE_CW, "OPEN_BRACKET")));
        openBattlePage();
        String row = router.render().rowText(5);

        assertTrue(row.contains("CLOSE_B~") && row.contains("OPEN_BR~"), row);
    }

    @Test
    void conflictMessagesUseTheRightParticleAndFitOnScreen() {
        assertEquals("'회전'과", SettingsScreen.quoted("회전"));
        assertEquals("'한 번에 떨어뜨리기'와", SettingsScreen.quoted("한 번에 떨어뜨리기"));
        assertEquals("'게임 메뉴'와", SettingsScreen.quoted("게임 메뉴"));
        assertEquals("'재개'와", SettingsScreen.quoted("재개"));
        String longest = "Space: 1P " + SettingsScreen.quoted("한 번에 떨어뜨리기") + " 겹칩니다";
        assertTrue(team.tetris.ui.render.TextFrame.displayWidth(longest) <= team.tetris.ui.render.TextFrame.COLUMNS - 2,
                longest);
    }

    @Test
    void everyHelpLineFitsOnScreen() {
        for (String line : SettingsScreen.allHelpLines()) {
            assertTrue(team.tetris.ui.render.TextFrame.displayWidth(line) <= team.tetris.ui.render.TextFrame.COLUMNS - 2,
                    line);
        }
    }
}
