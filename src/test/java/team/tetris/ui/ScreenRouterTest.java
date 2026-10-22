package team.tetris.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import team.tetris.application.GameCommand;
import team.tetris.application.GameStatus;
import team.tetris.application.model.GameMode;
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
        assertEquals(List.of("게임 시작", "아이템 모드", "설정", "스코어보드", "종료"),
                router.menuItems().stream().map(MenuScreen.MenuItem::label).toList());
    }

    @Test
    void theItemModeMenuStartsAnItemModeGame() {
        router.startItemGame();
        GameScreen game = assertInstanceOf(GameScreen.class, router.current());

        game.session().handle(GameCommand.QUIT_GAME);

        assertEquals(GameMode.ITEM, game.session().result().orElseThrow().mode());
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

    @ParameterizedTest
    @CsvSource({"D, d", "D, D", "DIGIT1, !", "SEMICOLON, :"})
    void heldDropKeyCannotTypeIntoTheNameAfterGameOver(String key, char character) throws Exception {
        NameEntryScreen entry = finishGameHolding(key);

        router.charTyped(character); // 전환 직전 눌림에 뒤따르는 문자 이벤트
        for (int i = 0; i < 3; i++) {
            router.keyPressed(key); // OS 자동 반복은 KEY_PRESSED와 KEY_TYPED 모두 발생시킨다.
            router.charTyped(character);
        }
        assertEquals("", entry.name());

        router.keyReleased(key);
        router.keyPressed(key);
        router.charTyped(character);
        router.keyReleased(key);
        TestApplication.tap(router, "ENTER");
        assertEquals(String.valueOf(character), app.scores().list().getFirst().name());
    }

    @Test
    void newTextKeysStillWorkWhileAnOldGameKeyIsHeld() throws Exception {
        NameEntryScreen entry = finishGameHolding("D");

        router.keyPressed("A");
        router.charTyped('A');
        router.keyReleased("A");
        router.keyPressed("D");
        router.charTyped('d');
        assertEquals("A", entry.name());

        router.keyReleased("D");
        router.keyPressed("D");
        router.charTyped('d');
        router.keyPressed("D");
        router.charTyped('d'); // 이름 화면에서 새로 누른 키의 정상 반복은 유지한다.
        router.keyReleased("D");
        router.charTyped('홍'); // 물리 키 식별자가 없는 입력기 확정 문자도 허용한다.
        assertEquals("Add홍", entry.name());
    }

    @Test
    void focusLossClearsTheOldKeysTextSuppression() throws Exception {
        NameEntryScreen entry = finishGameHolding("D");
        router.keyPressed("D");
        router.charTyped('d');
        assertEquals("", entry.name());

        router.focusLost(); // 포커스를 잃은 동안의 KEY_RELEASED는 전달되지 않을 수 있다.
        router.charTyped('홍');
        router.keyPressed("D");
        router.charTyped('d');
        assertEquals("홍d", entry.name());
    }

    private NameEntryScreen finishGameHolding(String dropKey) throws Exception {
        var defaults = Settings.defaults();
        var bindings = new java.util.EnumMap<>(defaults.keyBindings());
        bindings.put(GameCommand.HARD_DROP, dropKey);
        router.updateSettings(new Settings(defaults.screenSize(), bindings, defaults.colorBlindMode()));
        router.startGame();
        GameScreen game = assertInstanceOf(GameScreen.class, router.current());
        for (int i = 0; i < 200 && game.session().result().isEmpty(); i++) {
            router.keyReleased(dropKey);
            router.keyPressed(dropKey);
            router.update(TestApplication.FRAME);
        }
        assertEquals(GameStatus.GAME_OVER, game.session().snapshot().status());
        router.update(1_200_000_000L);
        return assertInstanceOf(NameEntryScreen.class, router.current());
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
