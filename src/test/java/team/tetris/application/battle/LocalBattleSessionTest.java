package team.tetris.application.battle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.BiFunction;
import org.junit.jupiter.api.Test;
import team.tetris.application.GameCommand;
import team.tetris.application.GameSnapshot;
import team.tetris.application.GameStatus;
import team.tetris.application.LineClearAnimation;
import team.tetris.application.ScorePolicy;
import team.tetris.application.SpeedRule;
import team.tetris.application.model.Difficulty;
import team.tetris.application.model.GameMode;
import team.tetris.core.ActivePiece;
import team.tetris.core.AttackBlock;
import team.tetris.core.Cell;
import team.tetris.core.PlayerEngine;
import team.tetris.core.Position;
import team.tetris.core.TetrominoType;
import team.tetris.core.port.TetrisEnginePort;
import team.tetris.core.rule.IncomingLines;
import team.tetris.core.rule.PieceGenerator;
import team.tetris.core.rule.PlainPieceSource;

/** 로컬 대전: 공격 전달, 승패, 무승부, 일시정지·나가기, 결과 확정 뒤 진행 중단 (3차 카드 17). */
class LocalBattleSessionTest {

    private static final int WIDTH = 10;
    private static final int HEIGHT = 8;
    private static final long SECOND = 1_000_000_000L;
    private static final BattlePlayer ONE = BattlePlayer.ONE;
    private static final BattlePlayer TWO = BattlePlayer.TWO;
    private static final Cell ATTACK = Cell.occupiedBy(AttackBlock.INSTANCE);

    /** 레벨 0, 1초마다 한 칸 떨어지는 속도. */
    private static final SpeedRule ONE_SECOND = new SpeedRule() {
        @Override
        public int levelFor(int totalClearedLines) {
            return 0;
        }

        @Override
        public long gravityIntervalNanos(int level) {
            return SECOND;
        }
    };

    private static Cell[][] empty() {
        Cell[][] board = new Cell[HEIGHT][WIDTH];
        for (Cell[] row : board) {
            Arrays.fill(row, Cell.EMPTY);
        }
        return board;
    }

    /** holes 열만 빈 T 칸 줄. */
    private static Cell[] row(int... holes) {
        Cell[] row = new Cell[WIDTH];
        Arrays.fill(row, Cell.occupiedBy(TetrominoType.T));
        for (int hole : holes) {
            row[hole] = Cell.EMPTY;
        }
        return row;
    }

    /** holes 열만 빈 공격 줄. */
    private static List<Cell> attack(int... holes) {
        List<Cell> line = new ArrayList<>(Collections.nCopies(WIDTH, ATTACK));
        for (int hole : holes) {
            line.set(hole, Cell.EMPTY);
        }
        return line;
    }

    /** 맨 아래 두 줄이 O(4·5열)로 채워지면 지워지는 보드. 떨어뜨리면 2줄 삭제 → 공격 2줄. */
    private static Cell[][] twoLineClear() {
        Cell[][] board = empty();
        board[6] = row(4, 5);
        board[7] = row(4, 5);
        return board;
    }

    /** O가 맨 위(0·1행)에서 더 내려갈 수 없어 고정되면 다음 O를 놓을 수 없는 보드. */
    private static Cell[][] blockedTop() {
        Cell[][] board = empty();
        board[2] = row(0);
        return board;
    }

    private static ActivePiece oAtTop() {
        return new ActivePiece(TetrominoType.O, 0, new Position(3, 0));
    }

    private static BiFunction<BattlePlayer, IncomingLines, TetrisEnginePort> engines(Cell[][] one, Cell[][] two) {
        return engines(one, oAtTop(), two, oAtTop());
    }

    private static BiFunction<BattlePlayer, IncomingLines, TetrisEnginePort> engines(
            Cell[][] one, ActivePiece startOne, Cell[][] two, ActivePiece startTwo) {
        return (player, incoming) -> new PlayerEngine(player == ONE ? one : two, player == ONE ? startOne : startTwo,
                new PlainPieceSource(new PieceGenerator() {
                    @Override
                    public TetrominoType next() {
                        return TetrominoType.O;
                    }

                    @Override
                    public TetrominoType peek() {
                        return TetrominoType.O;
                    }
                }), incoming);
    }

    private static LocalBattleSession battle(Cell[][] one, Cell[][] two) {
        return new LocalBattleSession(WIDTH, engines(one, two), new ScorePolicy(), ONE_SECOND,
                GameMode.NORMAL, Difficulty.NORMAL);
    }

    private static boolean hasAttackCells(GameSnapshot snapshot) {
        for (Cell[] row : snapshot.engine().board()) {
            for (Cell cell : row) {
                if (cell.equals(ATTACK)) {
                    return true;
                }
            }
        }
        return false;
    }

    @Test
    void clearingTwoLinesQueuesTheAttackUntilTheOpponentLocksItsNextPiece() {
        LocalBattleSession battle = battle(twoLineClear(), empty());

        battle.handle(ONE, GameCommand.HARD_DROP);

        BattleSnapshot afterAttack = battle.snapshot();
        assertEquals(List.of(attack(4, 5), attack(4, 5)), afterAttack.pendingFor(TWO));
        assertTrue(afterAttack.pendingFor(ONE).isEmpty());
        assertFalse(hasAttackCells(afterAttack.of(TWO)));

        // 상대가 다음 블록을 고정하면 줄 삭제 뒤, 다음 블록이 나오기 전에 아래로 들어온다.
        battle.handle(TWO, GameCommand.HARD_DROP);

        BattleSnapshot received = battle.snapshot();
        assertTrue(received.pendingFor(TWO).isEmpty());
        Cell[][] board = received.of(TWO).engine().board();
        assertEquals(attack(4, 5), List.of(board[6]));
        assertEquals(attack(4, 5), List.of(board[7]));
        assertEquals(TetrominoType.O, board[4][4].occupiedBy());
        assertEquals(BattleStatus.RUNNING, received.status());
        assertNotNull(received.of(TWO).engine().activePiece());
    }

    @Test
    void clearingASingleLineDoesNotAttack() {
        Cell[][] one = empty();
        one[7] = row(4, 5);
        LocalBattleSession battle = battle(one, empty());

        battle.handle(ONE, GameCommand.HARD_DROP);

        assertEquals(1, battle.snapshot().of(ONE).clearedLines());
        assertTrue(battle.snapshot().pendingFor(TWO).isEmpty());
    }

    @Test
    void attackLinesPushingABlockAboveTheBoardLoseTheGame() {
        // 2P는 3~7행이 쌓여 있어 O가 1·2행에 고정된다. 공격 2줄이 들어오면 1행의 O가 보드 위로 밀려 나간다.
        Cell[][] two = empty();
        for (int y = 3; y < HEIGHT; y++) {
            two[y] = row(0);
        }
        LocalBattleSession battle = battle(twoLineClear(), two);
        battle.handle(ONE, GameCommand.HARD_DROP);

        battle.handle(TWO, GameCommand.HARD_DROP);

        BattleResult result = battle.result().orElseThrow();
        assertEquals(ONE, result.winner());
        assertEquals(BattleEnd.TOPPED_OUT, result.end());
        assertEquals(BattleStatus.FINISHED, battle.snapshot().status());
    }

    @Test
    void theLossIsDecidedAtOnceEvenWhileTheLineClearAnimationIsRunning() {
        // 2P의 O가 7행을 채워 1줄을 지우고(연출 시작), 같은 고정에서 공격 2줄이 들어와 맨 위 칸이 밀려 나간다.
        // 1인 게임은 연출이 끝난 뒤 종료를 알리지만 대전은 그 자리에서 승패를 확정한다.
        Cell[][] two = empty();
        two[0][0] = Cell.occupiedBy(TetrominoType.Z);
        two[6] = row(0, 4, 5);
        two[7] = row(4, 5);
        LocalBattleSession battle = battle(twoLineClear(), two);
        battle.handle(ONE, GameCommand.HARD_DROP);

        battle.handle(TWO, GameCommand.HARD_DROP);

        assertEquals(ONE, battle.result().orElseThrow().winner());
        // 끝난 화면에는 멈춘 연출 대신 확정된 보드를 보여 주고, 진 쪽은 GAME_OVER로 알린다.
        assertNull(battle.snapshot().of(TWO).lineClearAnimation());
        assertEquals(GameStatus.GAME_OVER, battle.snapshot().of(TWO).status());
        assertEquals(1, battle.snapshot().of(TWO).clearedLines());
    }

    @Test
    void attacksArrivingDuringTheReceiversClearAnimationWaitForItsNextLock() {
        Cell[][] two = empty();
        two[7] = row(4, 5);
        LocalBattleSession battle = battle(twoLineClear(), two);
        battle.handle(TWO, GameCommand.HARD_DROP);
        assertNotNull(battle.snapshot().of(TWO).lineClearAnimation());

        battle.handle(ONE, GameCommand.HARD_DROP);
        battle.update(LineClearAnimation.DURATION_NANOS);

        assertNull(battle.snapshot().of(TWO).lineClearAnimation());
        assertEquals(2, battle.snapshot().pendingFor(TWO).size());
        assertFalse(hasAttackCells(battle.snapshot().of(TWO)));

        battle.handle(TWO, GameCommand.HARD_DROP);

        assertTrue(battle.snapshot().pendingFor(TWO).isEmpty());
        assertTrue(hasAttackCells(battle.snapshot().of(TWO)));
    }

    @Test
    void attacksMadeInAFrameReachTheOpponentOnlyAfterBothBoardsMoved() {
        // 두 O가 바닥 바로 위(6·7행)에 있어 다음 중력에 둘 다 고정된다. 공격하는 쪽이 1P든 2P든, 같은 프레임에
        // 고정한 상대는 그 공격을 바로 받지 않고 대기열에 남긴다 (먼저 진행하는 1P만 유리하지 않게).
        ActivePiece onFloor = new ActivePiece(TetrominoType.O, 0, new Position(3, 6));
        for (BattlePlayer attacker : BattlePlayer.values()) {
            Cell[][] attacking = twoLineClear();
            LocalBattleSession battle = new LocalBattleSession(WIDTH,
                    attacker == ONE ? engines(attacking, onFloor, empty(), onFloor)
                            : engines(empty(), onFloor, attacking, onFloor),
                    new ScorePolicy(), ONE_SECOND, GameMode.NORMAL, Difficulty.NORMAL);

            battle.update(SECOND);

            BattlePlayer receiver = attacker.opponent();
            assertEquals(2, battle.snapshot().of(attacker).clearedLines(), attacker::toString);
            assertEquals(2, battle.snapshot().pendingFor(receiver).size(), attacker::toString);
            assertFalse(hasAttackCells(battle.snapshot().of(receiver)), attacker::toString);
        }
    }

    @Test
    void attacksFromTheFrameThatEndsTheBattleAreNotQueued() {
        // 같은 프레임에 1P는 2줄을 지워 공격하고 2P는 쌓을 수 없게 된다. 결과가 나온 프레임의 공격은 넣지 않는다.
        ActivePiece onFloor = new ActivePiece(TetrominoType.O, 0, new Position(3, 6));
        LocalBattleSession battle = new LocalBattleSession(WIDTH, engines(twoLineClear(), onFloor, blockedTop(), oAtTop()),
                new ScorePolicy(), ONE_SECOND, GameMode.NORMAL, Difficulty.NORMAL);

        battle.update(SECOND);

        assertEquals(ONE, battle.result().orElseThrow().winner());
        assertEquals(2, battle.snapshot().of(ONE).clearedLines());
        assertTrue(battle.snapshot().pendingFor(TWO).isEmpty());
    }

    @Test
    void bothPlayersToppingOutInTheSameFrameIsADraw() {
        LocalBattleSession battle = battle(blockedTop(), blockedTop());

        battle.update(SECOND);

        BattleResult result = battle.result().orElseThrow();
        assertTrue(result.draw());
        assertEquals(BattleEnd.BOTH_TOPPED_OUT, result.end());
        assertTrue(result.winnerIfAny().isEmpty());
    }

    @Test
    void onlyThePlayerWhoToppedOutInTheFrameLoses() {
        LocalBattleSession battle = battle(blockedTop(), empty());

        battle.update(SECOND);

        assertEquals(TWO, battle.result().orElseThrow().winner());
        assertEquals(BattleEnd.TOPPED_OUT, battle.result().orElseThrow().end());
    }

    @Test
    void aLossFromAnInputIsDecidedBeforeTheOtherBoardMoves() {
        LocalBattleSession battle = battle(blockedTop(), blockedTop());

        battle.handle(ONE, GameCommand.HARD_DROP);

        assertEquals(TWO, battle.result().orElseThrow().winner());
        assertEquals(oAtTop(), battle.snapshot().of(TWO).engine().activePiece());
    }

    @Test
    void nothingChangesAfterTheResultIsDecided() {
        // 1P가 지고 나면 2P가 줄을 지워도 점수·공격이 생기지 않는다.
        LocalBattleSession battle = battle(blockedTop(), twoLineClear());
        battle.handle(ONE, GameCommand.HARD_DROP);
        BattleResult result = battle.result().orElseThrow();
        BattleSnapshot finished = battle.snapshot();

        battle.handle(TWO, GameCommand.HARD_DROP);
        battle.update(10 * SECOND);
        battle.handle(TWO, GameCommand.PAUSE);
        battle.handle(TWO, GameCommand.QUIT_GAME);
        battle.abort();

        assertSame(result, battle.result().orElseThrow());
        assertEquals(finished.of(TWO).score(), battle.snapshot().of(TWO).score());
        assertEquals(finished.of(TWO).engine().activePiece(), battle.snapshot().of(TWO).engine().activePiece());
        assertTrue(battle.snapshot().pendingFor(ONE).isEmpty());
        assertEquals(BattleStatus.FINISHED, battle.snapshot().status());
    }

    @Test
    void pausingByEitherPlayerStopsBothBoardsUntilResumed() {
        LocalBattleSession battle = battle(empty(), empty());

        battle.handle(ONE, GameCommand.PAUSE);
        battle.update(10 * SECOND);
        battle.handle(TWO, GameCommand.MOVE_LEFT);
        battle.handle(ONE, GameCommand.HARD_DROP);

        BattleSnapshot paused = battle.snapshot();
        assertEquals(BattleStatus.PAUSED, paused.status());
        assertEquals(GameStatus.PAUSED, paused.of(ONE).status());
        assertEquals(GameStatus.PAUSED, paused.of(TWO).status());
        assertEquals(oAtTop(), paused.of(ONE).engine().activePiece());
        assertEquals(oAtTop(), paused.of(TWO).engine().activePiece());

        battle.handle(TWO, GameCommand.RESUME);
        battle.update(SECOND);

        BattleSnapshot resumed = battle.snapshot();
        assertEquals(BattleStatus.RUNNING, resumed.status());
        assertEquals(GameStatus.RUNNING, resumed.of(ONE).status());
        assertEquals(new Position(3, 1), resumed.of(ONE).engine().activePiece().origin());
        assertEquals(new Position(3, 1), resumed.of(TWO).engine().activePiece().origin());
    }

    @Test
    void quittingWhilePausedLosesTheGameForTheQuitter() {
        LocalBattleSession battle = battle(empty(), empty());
        battle.handle(ONE, GameCommand.PAUSE);

        battle.handle(TWO, GameCommand.QUIT_GAME);

        assertEquals(ONE, battle.result().orElseThrow().winner());
        assertEquals(BattleEnd.FORFEIT, battle.result().orElseThrow().end());
    }

    @Test
    void abortingEndsTheBattleWithoutAWinner() {
        LocalBattleSession battle = battle(twoLineClear(), empty());
        battle.handle(ONE, GameCommand.HARD_DROP);

        battle.abort();

        BattleResult result = battle.result().orElseThrow();
        assertEquals(BattleEnd.ABORTED, result.end());
        assertTrue(result.winnerIfAny().isEmpty());
        assertFalse(result.draw());
        assertEquals(battle.snapshot().of(ONE).score(), result.scoreOf(ONE));
        assertEquals(0, result.scoreOf(TWO));
    }

    @Test
    void bothPlayersShareTheModeAndDifficulty() {
        LocalBattleSession battle = new LocalBattleSession(WIDTH, engines(empty(), empty()), new ScorePolicy(),
                ONE_SECOND, GameMode.ITEM, Difficulty.HARD);

        BattleSnapshot snapshot = battle.snapshot();

        for (BattlePlayer player : BattlePlayer.values()) {
            assertEquals(GameMode.ITEM, snapshot.of(player).mode());
            assertEquals(Difficulty.HARD, snapshot.of(player).difficulty());
        }
        assertEquals(GameMode.ITEM, snapshot.mode());
        assertEquals(Difficulty.HARD, snapshot.difficulty());
        assertTrue(battle.result().isEmpty());
    }

    @Test
    void invalidSetupsAndArgumentsAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> new LocalBattleSession(WIDTH + 1, engines(empty(), empty()),
                new ScorePolicy(), ONE_SECOND, GameMode.NORMAL, Difficulty.NORMAL));
        LocalBattleSession battle = battle(empty(), empty());
        assertThrows(IllegalArgumentException.class, () -> battle.update(-1));
        assertThrows(NullPointerException.class, () -> battle.handle(null, GameCommand.PAUSE));
        assertThrows(NullPointerException.class, () -> battle.handle(ONE, null));
        assertThrows(UnsupportedOperationException.class, () -> battle.snapshot().pendingOne().add(attack(0)));
    }

    @Test
    void resultsAndSnapshotsCheckTheirOwnConsistency() {
        assertThrows(IllegalArgumentException.class, () -> new BattleResult(ONE, BattleEnd.ABORTED, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new BattleResult(ONE, BattleEnd.BOTH_TOPPED_OUT, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new BattleResult(null, BattleEnd.FORFEIT, 0, 0));
        assertEquals(TWO, ONE.opponent());
        assertEquals(ONE, TWO.opponent());
        BattleSnapshot running = battle(empty(), empty()).snapshot();
        assertThrows(IllegalArgumentException.class, () -> new BattleSnapshot(running.one(), running.two(),
                List.of(), List.of(), BattleStatus.FINISHED, null, GameMode.NORMAL, Difficulty.NORMAL));
    }

    // ---- 시간제한 (R3-05, 안건 ⑪⑫⑱㉓) ----

    private static LocalBattleSession timed(Cell[][] one, Cell[][] two, long seconds) {
        return new LocalBattleSession(WIDTH, engines(one, two), new ScorePolicy(), ONE_SECOND,
                GameMode.NORMAL, Difficulty.NORMAL, java.time.Duration.ofSeconds(seconds));
    }

    @Test
    void inputsCountUntilTheExactMomentTheTimeRunsOutAndNotAfter() {
        LocalBattleSession battle = timed(empty(), empty(), 3);

        battle.update(3 * SECOND - 1);
        assertTrue(battle.result().isEmpty(), "만료 직전에는 진행 중");
        assertEquals(1, battle.snapshot().timer().remainingNanos());
        long before = battle.snapshot().of(TWO).score();
        battle.handle(TWO, GameCommand.HARD_DROP);
        long justBefore = battle.snapshot().of(TWO).score();
        assertTrue(justBefore > before, "만료 직전 입력의 점수는 반영된다");

        battle.update(1);

        BattleResult result = battle.result().orElseThrow();
        assertEquals(BattleEnd.TIME_UP, result.end());
        assertTrue(battle.snapshot().timer().expired());
        BattlePlayer higher = result.scoreOne() > result.scoreTwo() ? ONE : TWO;
        assertEquals(higher, result.winner());
        assertEquals(justBefore, result.scoreOf(TWO));

        battle.handle(ONE, GameCommand.HARD_DROP);
        battle.update(SECOND);
        assertEquals(result.scoreOne(), battle.snapshot().of(ONE).score(), "만료 뒤 입력은 반영되지 않는다");
    }

    @Test
    void aLongFrameMovesTheBoardsOnlyUpToTheTimeLimit() {
        // 2초 제한에 10초짜리 프레임이 와도 보드는 2초만큼(중력 두 번)만 진행한다.
        LocalBattleSession battle = timed(empty(), empty(), 2);

        battle.update(10 * SECOND);

        assertEquals(BattleEnd.TIME_UP_DRAW, battle.result().orElseThrow().end());
        assertEquals(new Position(3, 2), battle.snapshot().of(ONE).engine().activePiece().origin());
        assertEquals(new Position(3, 2), battle.snapshot().of(TWO).engine().activePiece().origin());
    }

    @Test
    void equalScoresWhenTheTimeRunsOutAreADraw() {
        LocalBattleSession battle = timed(empty(), empty(), 1);

        battle.update(SECOND);

        BattleResult result = battle.result().orElseThrow();
        assertEquals(result.scoreOne(), result.scoreTwo());
        assertEquals(BattleEnd.TIME_UP_DRAW, result.end());
        assertTrue(result.draw());
        assertTrue(result.winnerIfAny().isEmpty());
    }

    @Test
    void theClockStopsWhileThePlayersArePaused() {
        LocalBattleSession battle = timed(empty(), empty(), 3);
        battle.update(SECOND);

        battle.handle(ONE, GameCommand.PAUSE);
        battle.update(60 * SECOND);
        battle.handle(TWO, GameCommand.RESUME);

        assertEquals(2 * SECOND, battle.snapshot().timer().remainingNanos());
        battle.update(2 * SECOND - 1);
        assertTrue(battle.result().isEmpty());
        battle.update(1);
        assertTrue(battle.result().isPresent());
    }

    @Test
    void aLossInTheSameFrameAsTheTimeRunningOutWins() {
        // 1P는 1초 뒤 중력으로 고정되며 더 쌓을 수 없게 되고, 같은 프레임에 1초 제한도 끝난다.
        LocalBattleSession battle = timed(blockedTop(), empty(), 1);

        battle.update(SECOND);

        BattleResult result = battle.result().orElseThrow();
        assertEquals(BattleEnd.TOPPED_OUT, result.end());
        assertEquals(TWO, result.winner());
    }

    @Test
    void theTimeCanRunOutDuringALineClearAnimationWithoutAffectingTheResult() {
        LocalBattleSession battle = timed(twoLineClear(), empty(), 1);
        battle.handle(ONE, GameCommand.HARD_DROP);
        assertNotNull(battle.snapshot().of(ONE).lineClearAnimation());
        long cleared = battle.snapshot().of(ONE).score();

        battle.update(SECOND);

        BattleResult result = battle.result().orElseThrow();
        assertEquals(BattleEnd.TIME_UP, result.end());
        assertEquals(ONE, result.winner());
        assertEquals(cleared, result.scoreOne(), "줄을 지운 순간의 점수 그대로");
        assertNull(battle.snapshot().of(ONE).lineClearAnimation());
    }

    @Test
    void battlesWithoutATimeLimitHaveNoClockAndLimitsMustBePositive() {
        assertNull(battle(empty(), empty()).snapshot().timer());
        assertThrows(IllegalArgumentException.class, () -> timed(empty(), empty(), 0));
        assertThrows(IllegalArgumentException.class, () -> timed(empty(), empty(), -1));
        assertEquals(java.time.Duration.ofSeconds(5).toNanos(), timed(empty(), empty(), 5).snapshot().timer().limitNanos());
    }

    @Test
    void timersAndTimeUpResultsCheckTheirOwnValues() {
        assertThrows(IllegalArgumentException.class, () -> new BattleTimer(0, 0));
        assertThrows(IllegalArgumentException.class, () -> new BattleTimer(10, 11));
        assertThrows(IllegalArgumentException.class, () -> new BattleTimer(10, -1));
        assertEquals(2, new BattleTimer(5 * SECOND, SECOND + 1).remainingSeconds());
        assertEquals(1, new BattleTimer(5 * SECOND, 1).remainingSeconds());
        assertEquals(0, new BattleTimer(5 * SECOND, 0).remainingSeconds());
        assertThrows(IllegalArgumentException.class, () -> new BattleResult(null, BattleEnd.TIME_UP, 1, 1));
        assertThrows(IllegalArgumentException.class, () -> new BattleResult(ONE, BattleEnd.TIME_UP_DRAW, 1, 1));
        assertFalse(new BattleResult(ONE, BattleEnd.TIME_UP, 2, 1).draw());
    }
}
