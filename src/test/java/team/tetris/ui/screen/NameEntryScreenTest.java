package team.tetris.ui.screen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import team.tetris.application.EndGameView.Stage;
import team.tetris.core.TetrominoType;
import team.tetris.ui.ScreenRouter;
import team.tetris.ui.TestApplication;

class NameEntryScreenTest {

    private final TestApplication app = new TestApplication().withPieces(TetrominoType.O);
    private final ScreenRouter router = app.router();

    /** 키를 한 번 눌렀다 뗀다. */
    private void tap(String key) {
        TestApplication.tap(router, key);
    }
    private NameEntryScreen entry;

    @BeforeEach
    void reachNameEntry() {
        router.startGame();
        TestApplication.playUntilGameOver(router);
        entry = (NameEntryScreen) router.current();
    }

    private void type(String text) {
        for (char c : text.toCharArray()) {
            router.charTyped(c);
        }
    }

    @Test
    void enteringANameSavesItAndHighlightsItOnTheScoreboard() throws Exception {
        type("ABC");
        tap("ENTER");

        ScoreboardScreen board = assertInstanceOf(ScoreboardScreen.class, router.current());
        assertTrue(board.isAfterGame());
        assertTrue(board.highlightedRecordId().isPresent());
        assertEquals("ABC", app.scores().list().get(0).name());
        assertTrue(router.render().text().contains("ABC"));
    }

    @Test
    void koreanNamesAreAccepted() throws Exception {
        type("홍길동");
        assertTrue(router.render().text().contains("홍길동"));

        tap("ENTER");

        assertEquals("홍길동", app.scores().list().get(0).name());
    }

    @Test
    void blankNameIsRejectedWithAMessage() {
        type("   ");
        tap("ENTER");

        assertEquals(Stage.NAME_REQUIRED, entry.view().stage());
        assertTrue(entry.view().nameError().isPresent());
        assertTrue(router.render().text().contains("1~12자"));
    }

    @Test
    void backspaceRemovesTheLastCharacterAndControlCharactersAreIgnored() {
        type("AB\nC");
        tap("BACK_SPACE");

        assertEquals("AB", entry.name());
    }

    @Test
    void leadingSpacesFromAHeldSpaceBarAreIgnored() {
        type("   A B");

        assertEquals("A B", entry.name());
    }

    @Test
    void explainsHowToTypeKorean() {
        assertTrue(entry.acceptsTextInput());
        assertTrue(router.render().text().contains("한/영 키로 한글 입력"));
    }

    @Test
    void nameIsLimitedToTwelveCharacters() {
        type("ABCDEFGHIJKLMNOP");

        assertEquals(NameEntryScreen.MAX_NAME_LENGTH, entry.name().length());
    }

    @Test
    void closingTheWindowAsksForConfirmationFirst() {
        router.requestExit();
        assertTrue(entry.isConfirmingExit());
        assertEquals(0, app.exits());
        assertTrue(router.render().text().contains("기록하지 않고 종료할까요?"));

        tap("ESCAPE");
        assertFalse(entry.isConfirmingExit());

        router.requestExit();
        tap("ENTER");
        assertEquals(1, app.exits());
    }
}
