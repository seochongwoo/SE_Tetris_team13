package team.tetris.ui.screen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import team.tetris.application.battle.BattleEnd;
import team.tetris.application.battle.BattlePlayer;
import team.tetris.application.battle.BattleSnapshot;
import team.tetris.application.battle.BattleStatus;
import team.tetris.application.model.GameMode;
import team.tetris.core.Position;
import team.tetris.core.TetrominoType;
import team.tetris.ui.ScreenRouter;
import team.tetris.ui.TestApplication;
import team.tetris.ui.input.KeyNames;
import team.tetris.ui.input.RepeatController;
import team.tetris.ui.render.BattleRenderer;

/** 로컬 대전 화면: 메뉴 진입, 두 사람의 입력, 일시정지·기권·중단, 결과와 이후 흐름 (3차 카드 19). */
class BattleScreenTest {

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

    private Position origin(BattlePlayer player) {
        return snapshot().of(player).engine().activePiece().origin();
    }

    @Test
    void theStartMenuLeadsToTheModeChoiceAndIntoABattle() {
        router.showMenu();
        tap(KeyNames.DOWN);
        tap(KeyNames.DOWN);
        tap(KeyNames.DOWN);
        tap(KeyNames.ENTER);
        MenuScreen modes = assertInstanceOf(MenuScreen.class, router.current());
        String text = router.render().text();
        assertTrue(text.contains("일반 대전") && text.contains("아이템 대전") && text.contains("시간제한"));

        tap(KeyNames.DOWN);
        tap(KeyNames.ENTER);

        assertEquals(GameMode.ITEM, snapshot().mode());
        assertEquals(BattleRenderer.COLUMNS, router.render().columns());
        assertEquals(0, modes.cursor() - 1);
    }

    @Test
    void theTimeLimitEntryIsNotYetSelectableAndEscapeGoesBack() {
        router.showBattleModes();
        tap(KeyNames.DOWN);
        tap(KeyNames.DOWN);
        tap(KeyNames.ENTER);
        assertInstanceOf(MenuScreen.class, router.current());

        tap(KeyNames.ESCAPE);

        MenuScreen main = assertInstanceOf(MenuScreen.class, router.current());
        assertEquals(router.menuItems().size(), main.items().size());
    }

    @Test
    void eachPlayersKeysMoveOnlyTheirOwnPiece() {
        router.startBattle(GameMode.NORMAL);
        Position one = origin(BattlePlayer.ONE);
        Position two = origin(BattlePlayer.TWO);

        tap("A");
        tap(KeyNames.RIGHT);
        tap(KeyNames.RIGHT);

        assertEquals(one.translate(-1, 0), origin(BattlePlayer.ONE));
        assertEquals(two.translate(2, 0), origin(BattlePlayer.TWO));

        tap("SPACE");
        assertEquals(0, snapshot().of(BattlePlayer.TWO).score());
        assertTrue(snapshot().of(BattlePlayer.ONE).score() > 0, "1P 하드드롭 점수");
        tap(KeyNames.ENTER);
        assertTrue(snapshot().of(BattlePlayer.TWO).score() > 0, "2P 하드드롭 점수");
    }

    @Test
    void holdingKeysAtTheSameTimeRepeatsForBothPlayers() {
        // H3-06: 두 명이 동시에 누르고 있어도 서로의 반복이 끊기지 않는다.
        router.startBattle(GameMode.NORMAL);
        Position one = origin(BattlePlayer.ONE);
        Position two = origin(BattlePlayer.TWO);

        router.keyPressed("A");
        router.keyPressed(KeyNames.RIGHT);
        for (int i = 0; i < 20; i++) {
            router.update(FRAME);
        }
        router.keyReleased("A");
        router.keyReleased(KeyNames.RIGHT);

        assertTrue(origin(BattlePlayer.ONE).x() <= one.x() - 2, "1P 왼쪽 반복");
        assertTrue(origin(BattlePlayer.TWO).x() >= two.x() + 2, "2P 오른쪽 반복");
        assertTrue(RepeatController.DEFAULT_INITIAL_DELAY_NANOS < 20 * FRAME);
    }

    @Test
    void anAttackIsShownInTheReceiversAreaAndThePlayersInputWaitsForItsOwnClearAnimation() {
        // O만 나오게 하면 1P가 다섯 개를 나란히 놓아 2줄을 지운다 → 2P에게 공격 2줄.
        app.withPieces(TetrominoType.O);
        router.startBattle(GameMode.NORMAL);
        int[] shifts = {-4, -2, 0, 2, 4};
        for (int shift : shifts) {
            for (int i = 0; i < Math.abs(shift); i++) {
                tap(shift < 0 ? "A" : "D");
            }
            tap("SPACE");
        }
        assertEquals(2, snapshot().of(BattlePlayer.ONE).clearedLines());
        assertEquals(2, snapshot().pendingFor(BattlePlayer.TWO).size());
        assertTrue(router.render().text().contains("2/10"));
        assertNotNull(snapshot().of(BattlePlayer.ONE).lineClearAnimation());

        // 1P가 연출 중에 누른 조작은 보관되고, 2P는 그대로 움직인다.
        Position one = origin(BattlePlayer.ONE);
        Position two = origin(BattlePlayer.TWO);
        TestApplication.tap(router, "A");
        TestApplication.tap(router, KeyNames.LEFT);
        router.update(FRAME);
        assertEquals(one, origin(BattlePlayer.ONE));
        assertEquals(two.translate(-1, 0), origin(BattlePlayer.TWO));

        for (int i = 0; i < 25 && snapshot().of(BattlePlayer.ONE).lineClearAnimation() != null; i++) {
            router.update(FRAME);
        }
        router.update(FRAME);
        assertNull(snapshot().of(BattlePlayer.ONE).lineClearAnimation());
        assertEquals(one.x() - 1, origin(BattlePlayer.ONE).x());
    }

    @Test
    void thePauseKeyStopsBothBoardsAndOpensTheMenu() {
        router.startBattle(GameMode.NORMAL);

        tap("P");

        assertEquals(BattleStatus.PAUSED, snapshot().status());
        String text = router.render().text();
        assertTrue(text.contains("일시정지") && text.contains("1P 기권") && text.contains("2P 기권"));
        Position one = origin(BattlePlayer.ONE);
        tap("A");
        router.update(5_000_000_000L);
        assertEquals(one, origin(BattlePlayer.ONE));

        tap("P");
        assertEquals(BattleStatus.RUNNING, snapshot().status());
    }

    @Test
    void theMenuKeyAlsoPausesAndLosingFocusPauses() {
        router.startBattle(GameMode.NORMAL);
        tap(KeyNames.ESCAPE);
        assertEquals(BattleStatus.PAUSED, snapshot().status());
        tap(KeyNames.ESCAPE);
        assertEquals(BattleStatus.RUNNING, snapshot().status());

        router.focusLost();
        router.update(FRAME);

        assertEquals(BattleStatus.PAUSED, snapshot().status());
    }

    @Test
    void forfeitingFromThePauseMenuGivesTheOtherPlayerTheWinAndRecordsNothing() {
        router.startBattle(GameMode.NORMAL);
        tap("P");
        tap(KeyNames.DOWN);
        tap(KeyNames.DOWN);
        tap(KeyNames.ENTER); // 2P 기권

        var result = battle().session().result().orElseThrow();
        assertEquals(BattlePlayer.ONE, result.winner());
        assertEquals(BattleEnd.FORFEIT, result.end());
        String text = router.render().text();
        assertTrue(text.contains("1P 승리 (기권승)") && text.contains("WIN") && text.contains("LOSE"));
        assertTrue(text.contains("다시 대전") && text.contains("메뉴로"));

        // 결과 직후에는 조작 키가 그대로 메뉴를 고르지 않는다.
        tap(KeyNames.DOWN);
        tap(KeyNames.ENTER);
        assertSame(BattleScreen.class, router.current().getClass());

        router.update(BattleScreen.RESULT_INPUT_DELAY_NANOS);
        tap(KeyNames.DOWN);
        tap(KeyNames.ENTER);

        assertInstanceOf(MenuScreen.class, router.current());
        assertTrue(app.scores().loadOrEmpty().value().isEmpty(), "대전 결과는 기록하지 않는다");
    }

    @Test
    void quittingWithoutAWinnerReturnsToTheMenu() {
        router.startBattle(GameMode.NORMAL);
        var session = battle().session();
        tap("P");
        for (int i = 0; i < 3; i++) {
            tap("S");
        }

        tap("SPACE"); // 그만두고 메뉴로

        assertEquals(BattleEnd.ABORTED, session.result().orElseThrow().end());
        assertInstanceOf(MenuScreen.class, router.current());
    }

    @Test
    void toppingOutShowsTheWinnerAndARematchStartsAFreshBattle() {
        router.startBattle(GameMode.NORMAL);
        var first = battle().session();
        for (int i = 0; i < 100 && first.result().isEmpty(); i++) {
            tap("SPACE");
        }

        var result = first.result().orElseThrow();
        assertEquals(BattlePlayer.TWO, result.winner());
        assertEquals(BattleEnd.TOPPED_OUT, result.end());
        assertTrue(router.render().text().contains("2P 승리!"));

        router.update(BattleScreen.RESULT_INPUT_DELAY_NANOS);
        tap(KeyNames.ENTER); // 다시 대전

        assertNotSame(first, battle().session());
        assertTrue(battle().session().result().isEmpty());
        assertEquals(GameMode.NORMAL, snapshot().mode());
    }

    @ParameterizedTest
    @ValueSource(strings = {"ENTER", "SPACE"})
    void holdingSelectDuringResultDelayRequiresReleaseBeforeRematch(String key) {
        // 리뷰 지적: 대기 중에 누른 키가 기록되지 않아, 대기 뒤 OS 자동 반복이 바로 재경기를 시작했다.
        router.startBattle(GameMode.NORMAL);
        tap("P");
        tap(KeyNames.DOWN);
        tap(KeyNames.ENTER); // 1P 기권
        BattleScreen resultScreen = battle();
        assertTrue(resultScreen.session().result().isPresent());

        router.keyPressed(key);
        router.update(BattleScreen.RESULT_INPUT_DELAY_NANOS);
        router.keyPressed(key); // OS 자동 반복
        router.update(FRAME);
        assertSame(resultScreen, router.current());

        router.keyReleased(key);
        tap(key);
        assertNotSame(resultScreen, router.current());
        assertTrue(battle().session().result().isEmpty());
    }

    @Test
    void theResultTitlesCoverEveryEnding() {
        assertEquals("무승부", BattleScreen.resultTitle(
                new team.tetris.application.battle.BattleResult(null, BattleEnd.BOTH_TOPPED_OUT, 1, 2)));
        assertEquals("대전 중단", BattleScreen.resultTitle(
                new team.tetris.application.battle.BattleResult(null, BattleEnd.ABORTED, 1, 2)));
        assertEquals("2P 승리!", BattleScreen.resultTitle(
                new team.tetris.application.battle.BattleResult(BattlePlayer.TWO, BattleEnd.TOPPED_OUT, 1, 2)));
        assertFalse(BattleScreen.PAUSE_ITEMS.isEmpty());
    }
}
