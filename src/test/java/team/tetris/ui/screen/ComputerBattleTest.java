package team.tetris.ui.screen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import team.tetris.application.GameCommand;
import team.tetris.application.battle.BattleEnd;
import team.tetris.application.battle.BattlePlayer;
import team.tetris.application.battle.BattleSnapshot;
import team.tetris.application.battle.BattleStatus;
import team.tetris.application.battle.ComputerPlayer;
import team.tetris.application.model.Difficulty;
import team.tetris.application.model.GameMode;
import team.tetris.core.Position;
import team.tetris.ui.ScreenRouter;
import team.tetris.ui.TestApplication;
import team.tetris.ui.input.KeyNames;
import team.tetris.ui.render.BattleRenderer;

/** 컴퓨터 대전 (도전 과제 CH-1): 메뉴, 2P를 대신하는 컴퓨터, 일시정지·결과. */
class ComputerBattleTest {

    private static final long FRAME = TestApplication.FRAME;

    private final TestApplication app = new TestApplication();
    private final ScreenRouter router = app.router();

    private void tap(String key) {
        TestApplication.tap(router, key);
        router.update(FRAME);
    }

    private BattleScreen battle() {
        return assertInstanceOf(BattleScreen.class, router.current());
    }

    private BattleSnapshot snapshot() {
        return battle().session().snapshot();
    }

    private void run(long nanos) {
        for (long t = 0; t < nanos && battle().session().result().isEmpty(); t += FRAME) {
            router.update(FRAME);
        }
    }

    @Test
    void theBattleMenuLeadsToAComputerBattleInEitherMode() {
        router.showBattleModes();
        for (int i = 0; i < 3; i++) {
            tap(KeyNames.DOWN);
        }
        tap(KeyNames.ENTER);
        MenuScreen modes = assertInstanceOf(MenuScreen.class, router.current());
        assertEquals("컴퓨터 대전", modes.heading().orElseThrow());

        tap(KeyNames.DOWN);
        tap(KeyNames.ENTER);
        MenuScreen sides = assertInstanceOf(MenuScreen.class, router.current());
        assertEquals("내 자리 선택", sides.heading().orElseThrow());
        String sideText = router.render().text();
        assertTrue(sideText.contains("1P · 왼쪽") && sideText.contains("2P · 오른쪽"), sideText);
        tap(KeyNames.ENTER);

        assertTrue(battle().againstComputer());
        assertEquals(java.util.Optional.of(BattlePlayer.TWO), battle().computerSide());
        assertEquals(GameMode.ITEM, snapshot().mode());
        String text = router.render().text();
        assertTrue(text.contains(BattleRenderer.COMPUTER_LABEL), text);
        assertTrue(text.contains("컴퓨터가 0.75초마다 키를 누릅니다"), text);
        assertFalse(router.render().rowText(BattleRenderer.ROWS - 1).contains("낙하  ←"), "2P 키 안내 대신 컴퓨터 안내");

        router.showComputerSides(GameMode.NORMAL);
        tap(KeyNames.UP);
        tap(KeyNames.ENTER);
        assertEquals("컴퓨터 대전", ((MenuScreen) router.current()).heading().orElseThrow());
        tap(KeyNames.UP);
        tap(KeyNames.ENTER);
        assertInstanceOf(MenuScreen.class, router.current());
        assertEquals("대전 모드 선택", ((MenuScreen) router.current()).heading().orElseThrow());
    }

    @Test
    void theComputerMovesTheSecondBoardOneKeyPerIntervalAndIgnoresThe2pKeys() {
        router.startComputerBattle(GameMode.NORMAL, BattlePlayer.ONE);
        long interval = ComputerPlayer.intervalNanos(snapshot().difficulty());
        Position start = snapshot().two().engine().activePiece().origin();
        int rotation = snapshot().two().engine().activePiece().rotation();

        String twoLeft = battle().keys().keyFor(BattlePlayer.TWO, GameCommand.MOVE_LEFT);
        tap(twoLeft);
        tap(twoLeft);
        assertEquals(start.x(), snapshot().two().engine().activePiece().origin().x(), "사람의 2P 키는 받지 않는다");

        long elapsed = 3 * FRAME;
        while (elapsed + FRAME < interval) {
            router.update(FRAME);
            elapsed += FRAME;
        }
        var piece = snapshot().two().engine().activePiece();
        assertEquals(start.x(), piece.origin().x(), "간격 전에는 누르지 않는다");
        assertEquals(rotation, piece.rotation());

        router.update(FRAME);
        router.update(FRAME);
        var moved = snapshot().two().engine().activePiece();
        assertTrue(moved != piece && (moved.origin().x() != start.x() || moved.rotation() != rotation
                || moved.piece() != piece.piece()), "간격이 되면 키 하나를 누른다");
    }

    @Test
    void theComputerOutlastsAnIdlePlayerAndClearsLines() {
        router.startComputerBattle(GameMode.NORMAL, BattlePlayer.ONE);
        assertEquals(Difficulty.NORMAL, snapshot().difficulty());

        run(15L * 60 * 1_000_000_000L);

        var result = battle().session().result().orElseThrow();
        assertEquals(BattlePlayer.TWO, result.winner());
        assertEquals(BattleEnd.TOPPED_OUT, result.end());
        assertTrue(snapshot().two().clearedLines() > 0, "컴퓨터가 줄을 지운다");
        assertTrue(router.render().text().contains("컴퓨터 승리!"), router.render().text());
        assertTrue(router.render().text().contains("1P " + result.scoreOne() + "  :  " + result.scoreTwo() + " 컴퓨터"),
                "점수 줄에도 컴퓨터 이름");
    }

    @Test
    void thePauseMenuOffersOneForfeitAndARematchStaysAgainstTheComputer() {
        router.startComputerBattle(GameMode.ITEM, BattlePlayer.ONE);
        tap(battle().keys().pauseKey());
        assertEquals(BattleStatus.PAUSED, snapshot().status());
        assertEquals(BattleScreen.COMPUTER_PAUSE_ITEMS, battle().pauseItems());
        String text = router.render().text();
        assertFalse(text.contains("2P 기권"));

        long before = snapshot().two().engine().activePiece().origin().y();
        run(5_000_000_000L);
        assertEquals(before, snapshot().two().engine().activePiece().origin().y(), "정지 중에는 컴퓨터도 멈춘다");

        tap(KeyNames.DOWN);
        tap(KeyNames.ENTER);
        var result = battle().session().result().orElseThrow();
        assertEquals(BattlePlayer.TWO, result.winner());
        assertEquals(BattleEnd.FORFEIT, result.end());
        assertTrue(router.render().text().contains("컴퓨터 승리 (기권승)"));

        router.update(BattleScreen.RESULT_INPUT_DELAY_NANOS);
        BattleScreen first = battle();
        tap(KeyNames.ENTER);
        assertTrue(battle() != first && battle().againstComputer(), "다시 대전도 컴퓨터와");
        assertEquals(GameMode.ITEM, snapshot().mode());
    }

    @Test
    void pauseMenuItemsOfTheComputerBattleEachDoTheirJob() {
        router.startComputerBattle(GameMode.NORMAL, BattlePlayer.ONE);
        tap(battle().keys().pauseKey());
        tap(KeyNames.ENTER);
        assertEquals(BattleStatus.RUNNING, snapshot().status(), "재개");

        tap(battle().keys().pauseKey());
        tap(KeyNames.DOWN);
        tap(KeyNames.DOWN);
        tap(KeyNames.ENTER);
        assertInstanceOf(MenuScreen.class, router.current());

        router.startComputerBattle(GameMode.NORMAL, BattlePlayer.ONE);
        tap(battle().keys().pauseKey());
        tap(KeyNames.UP);
        tap(KeyNames.ENTER);
        assertEquals(1, app.exits());
    }

    @Test
    void onHardTheComputerPressesEveryHalfSecondAndSaysItLooksAtTheNextPiece() throws Exception {
        app.settings().update(team.tetris.application.model.Settings.defaults().withDifficulty(Difficulty.HARD));
        ScreenRouter hard = app.router();
        hard.startComputerBattle(GameMode.NORMAL, BattlePlayer.ONE);
        String help = hard.render().rowText(BattleRenderer.ROWS - 1);
        assertTrue(help.contains("컴퓨터가 0.5초마다 누르고 NEXT까지 봄"), help);
        assertFalse(help.contains("~"), "잘리지 않는다");
    }

    @Test
    void playingAs2pPutsTheComputerOnTheLeftAndUsesThe2pKeys() {
        router.showComputerSides(GameMode.ITEM);
        tap(KeyNames.DOWN);
        tap(KeyNames.ENTER);
        assertEquals(java.util.Optional.of(BattlePlayer.ONE), battle().computerSide());
        assertEquals(GameMode.ITEM, snapshot().mode());

        // 왼쪽 이름이 컴퓨터이고, 왼쪽 아래에 조작 키 대신 컴퓨터 안내가 나온다.
        var frame = router.render();
        assertTrue(frame.rowText(1).substring(0, BattleRenderer.leftOf(BattlePlayer.TWO)).contains("컴퓨터"));
        String help = frame.rowText(BattleRenderer.ROWS - 1);
        assertTrue(help.startsWith(" 컴퓨터가 0.75초마다"), help);
        assertTrue(help.contains("낙하"), "오른쪽에는 2P 키 안내");

        Position one = snapshot().one().engine().activePiece().origin();
        Position two = snapshot().two().engine().activePiece().origin();
        tap(battle().keys().keyFor(BattlePlayer.ONE, GameCommand.MOVE_LEFT));
        assertEquals(one, snapshot().one().engine().activePiece().origin(), "1P 키는 받지 않는다");
        tap(battle().keys().keyFor(BattlePlayer.TWO, GameCommand.MOVE_LEFT));
        assertEquals(two.x() - 1, snapshot().two().engine().activePiece().origin().x(), "2P 키로 내 보드를 움직인다");

        // 컴퓨터는 왼쪽 보드를 움직인다.
        var before = snapshot().one().engine().activePiece();
        run(2 * ComputerPlayer.intervalNanos(snapshot().difficulty()));
        assertTrue(before != snapshot().one().engine().activePiece());

        // 기권하면 사람(2P)이 지고, 다시 대전도 같은 자리로 한다.
        tap(battle().keys().pauseKey());
        tap(KeyNames.DOWN);
        tap(KeyNames.ENTER);
        var result = battle().session().result().orElseThrow();
        assertEquals(BattlePlayer.ONE, result.winner());
        String text = router.render().text();
        assertTrue(text.contains("컴퓨터 승리 (기권승)"), text);
        assertTrue(text.contains("컴퓨터 " + result.scoreOne() + "  :  " + result.scoreTwo() + " 2P"), text);
        router.update(BattleScreen.RESULT_INPUT_DELAY_NANOS);
        tap(KeyNames.ENTER);
        assertEquals(java.util.Optional.of(BattlePlayer.ONE), battle().computerSide());
    }

    @Test
    void theComputerOnTheLeftAlsoBeatsAnIdlePlayer() {
        router.startComputerBattle(GameMode.NORMAL, BattlePlayer.TWO);
        run(15L * 60 * 1_000_000_000L);
        assertEquals(BattlePlayer.ONE, battle().session().result().orElseThrow().winner());
        assertTrue(snapshot().one().clearedLines() > 0);
    }

    @Test
    void twoPeopleStillHaveNoComputer() {
        router.startBattle(GameMode.NORMAL);
        assertFalse(battle().againstComputer());
        assertTrue(battle().computerSide().isEmpty());
        assertFalse(router.render().text().contains("컴퓨터"));
    }
}
