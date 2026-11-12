package team.tetris.application.network;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import team.tetris.application.GameCommand;
import team.tetris.application.network.MatchMessage.*;
import team.tetris.core.Cell;
import team.tetris.core.TetrominoType;
import static org.junit.jupiter.api.Assertions.*;
import static team.tetris.application.network.P2pMatchTestSupport.*;

class P2pMatchTest {
    private static void assertResult(Outcome outcome, EndReason reason, Result actual) {
        assertEquals(outcome, actual.outcome());
        assertEquals(reason, actual.reason());
    }

    @Test void delayedResultRestoresBothViewsToServerDecisionSnapshot() {
        try (var p = new Pair(Mode.TIMED, null, null)) {
            p.s.update(60_000_000_000L);
            assertTrue(p.s.result().isPresent());
            var serverView = p.s.local();
            var clientAtDecision = p.s.remote().orElseThrow();
            // 결과가 네트워크에서 지연되는 동안 클라이언트는 아직 진행 중이다.
            var delayed = new java.util.ArrayList<>(p.ct.incoming);
            p.ct.incoming.clear();
            p.c.handle(GameCommand.HARD_DROP);
            assertTrue(p.c.local().score() > clientAtDecision.score());
            p.ct.incoming.addAll(delayed);
            p.c.update(0);
            assertEquals(p.s.result(), p.c.result());
            assertEquals(clientAtDecision, p.c.local());
            assertEquals(serverView, p.c.remote().orElseThrow());
            p.s.update(0); // 늦게 도착한 클라이언트 상태도 확정 화면을 바꾸면 안 된다.
            assertEquals(clientAtDecision, p.s.remote().orElseThrow());
        }
    }
    @Test void simultaneousDefeatIsDrawAndTimedTieIsDraw() {
        var board = empty();
        for (int x = 0; x < 10; x++) board[2][x] = Cell.occupiedBy(TetrominoType.T);
        try (var p = new Pair(Mode.NORMAL, board, board)) {
            p.c.handle(GameCommand.HARD_DROP);
            p.s.update(1_000_000_000L); p.pump();
            assertResult(Outcome.DRAW, EndReason.TOP_OUT, p.s.result().orElseThrow());
            assertEquals(p.s.result(), p.c.result());
        }
        for (int advantage : new int[]{-1, 0, 1}) {
            try (var p = new Pair(Mode.TIMED, null, null)) {
                team.tetris.application.SpeedRule frozenGravity = new team.tetris.application.SpeedRule() {
                    public int levelFor(int lines) { return 0; }
                    public long gravityIntervalNanos(int level) { return Long.MAX_VALUE; }
                };
                p.s = new P2pMatch(p.ss, incoming -> new team.tetris.core.PlayerEngine(10, 20,
                        team.tetris.ui.TestApplication.constant(TetrominoType.O)), new team.tetris.application.ScorePolicy(), frozenGravity);
                p.c = new P2pMatch(p.cs, incoming -> new team.tetris.core.PlayerEngine(10, 20,
                        team.tetris.ui.TestApplication.constant(TetrominoType.O)), new team.tetris.application.ScorePolicy(), frozenGravity);
                if (advantage > 0) p.s.handle(GameCommand.SOFT_DROP);
                if (advantage < 0) p.c.handle(GameCommand.SOFT_DROP);
                p.pump(); p.s.update(60_000_000_000L); p.pump();
                assertResult(advantage == 0 ? Outcome.DRAW : advantage > 0 ? Outcome.SERVER_WINS
                        : Outcome.CLIENT_WINS, EndReason.TIME_LIMIT, p.s.result().orElseThrow());
                assertEquals(p.s.result(), p.c.result());
            }
        }
    }
    @ParameterizedTest @EnumSource(Mode.class)
    void bothScreensAttackTopOutAndSecondMatch(Mode mode) {
        try (var pair = new Pair(mode, twoLines(), empty())) {
            var s = pair.s; var c = pair.c;
            assertEquals(s.local(), c.remote().orElseThrow());
            s.handle(GameCommand.HARD_DROP); pair.pump();
            assertEquals(List.of(975, 975), c.local().pendingRows());
            assertEquals(s.local(), c.remote().orElseThrow());
            c.handle(GameCommand.HARD_DROP); pair.pump();
            assertTrue(c.local().pendingRows().isEmpty());
            assertEquals(PlayerState.ShapeCode.ATTACK, c.local().board().get(190).shape());
            for (int i = 0; i < 100 && s.result().isEmpty(); i++) {
                c.handle(GameCommand.HARD_DROP); pair.pump();
            }
            assertResult(Outcome.SERVER_WINS, EndReason.TOP_OUT, s.result().orElseThrow());
            assertEquals(s.result(), c.result());
            var frozen = c.local(); c.handle(GameCommand.HARD_DROP); c.update(10_000_000_000L);
            assertEquals(frozen, c.local());
            pair.rematch();
            assertEquals(2, pair.s.matchId());
            assertEquals(0, pair.c.local().score());
            assertEquals(0, pair.c.local().doubleScoreClears());
            assertTrue(pair.c.local().pendingRows().isEmpty());
            assertEquals(pair.s.local(), pair.c.local());
            assertFalse(pair.server.localReady()); assertFalse(pair.client.remoteReady());
            // 이전 경기 이벤트는 새 sequence여도 차단하며, 뒤의 정상 화면은 계속 처리한다.
            pair.ct.incoming.add(new MatchEnvelope(1, 100_000, new Attack(999, List.of(1, 2))));
            pair.c.update(0);
            assertTrue(pair.c.local().pendingRows().isEmpty());
            pair.s.handle(GameCommand.QUIT_GAME); pair.pump();
            assertResult(Outcome.CLIENT_WINS, EndReason.LEFT, pair.c.result().orElseThrow());
        }
    }

    @Test void pauseOwnerAndServerClockAreAuthoritative() {
        try (var p = new Pair(Mode.TIMED, null, null)) {
            p.c.update(9_000_000_000L);
            assertEquals(60_000_000_000L, p.c.remainingNanos());
            p.s.update(1_000_000_000L); p.pump();
            assertEquals(59_000_000_000L, p.c.remainingNanos());
            p.c.handle(GameCommand.PAUSE); p.pump();
            assertTrue(p.s.paused()); assertEquals(Role.CLIENT, p.s.pauseOwner());
            var frozen = p.s.local();
            p.s.update(5_000_000_000L); p.c.update(5_000_000_000L);
            assertEquals(frozen, p.s.local()); assertEquals(59_000_000_000L, p.s.remainingNanos());
            p.s.handle(GameCommand.RESUME); p.pump(); assertTrue(p.s.paused());
            p.c.handle(GameCommand.RESUME); p.s.update(20_000_000_000L); p.pump();
            assertFalse(p.s.paused()); assertFalse(p.c.paused());
            assertEquals(59_000_000_000L, p.s.remainingNanos());
            p.s.handle(GameCommand.PAUSE); p.pump();
            p.c.handle(GameCommand.QUIT_GAME); p.pump();
            assertResult(Outcome.SERVER_WINS, EndReason.LEFT, p.s.result().orElseThrow());
            assertEquals(p.s.result(), p.c.result());
        }
    }

    @Test void duplicateAttackAndAttackInFlightDuringPause() {
        try (var p = new Pair(Mode.NORMAL, null, null)) {
            p.s.handle(GameCommand.PAUSE); p.pump();
            var before = p.s.local().board();
            p.cs.connection().send(1, new Attack(1, List.of(1, 2)));
            p.st.incoming.add(new MatchEnvelope(1, 9999, new Attack(1, List.of(1, 2))));
            p.s.update(1_000_000_000L);
            assertEquals(List.of(1, 2), p.s.local().pendingRows());
            assertEquals(before, p.s.local().board());
            p.s.handle(GameCommand.RESUME); p.s.handle(GameCommand.HARD_DROP);
            assertTrue(p.s.local().pendingRows().isEmpty());
        }
    }

    @Test void exactTimeoutUsesScoresAndFreezesFurtherInput() {
        try (var p = new Pair(Mode.TIMED, null, null)) {
            p.c.handle(GameCommand.SOFT_DROP); p.pump();
            // 삭제 연출/중력 진행과 무관하게 서버 시간만으로 만료한다.
            p.s.update(59_999_999_999L); p.pump();
            assertTrue(p.s.result().isEmpty());
            p.s.update(1); p.pump();
            assertEquals(0, p.s.remainingNanos());
            assertEquals(EndReason.TIME_LIMIT, p.s.result().orElseThrow().reason());
            assertEquals(p.s.result(), p.c.result());
            var frozen = p.s.local();
            p.s.handle(GameCommand.HARD_DROP); p.s.update(Long.MAX_VALUE);
            assertEquals(frozen, p.s.local());
            assertThrows(IllegalArgumentException.class, () -> p.s.update(-1));
        }
    }

    @Test void sameFrameDefeatBeatsTimeLimitAndNoAttackAfterResult() {
        var board = empty();
        for (int x = 0; x < 10; x++) board[2][x] = Cell.occupiedBy(TetrominoType.T);
        try (var p = new Pair(Mode.TIMED, board, null)) {
            p.s.update(60_000_000_000L); p.pump();
            assertResult(Outcome.CLIENT_WINS, EndReason.TOP_OUT, p.s.result().orElseThrow());
            var frozen = p.c.local();
            p.ct.incoming.add(new MatchEnvelope(1, 10000, new Attack(9, List.of(1, 2))));
            p.c.update(0);
            assertEquals(frozen, p.c.local());
        }
    }

    @Test void resultLeavesNextLobbyMessageForLobbyAndConnectionFailureStopsPlay() {
        try (var p = new Pair(Mode.NORMAL, null, null)) {
            p.s.handle(GameCommand.QUIT_GAME);
            p.server.returnAfterResult(); // client가 RESULT를 읽기 전에 다음 LOBBY가 도착
            p.c.update(0);
            assertTrue(p.c.result().isPresent());
            p.client.returnAfterResult(); p.client.update();
            assertTrue(p.client.configuration().isPresent());
        }
        try (var p = new Pair(Mode.NORMAL, null, null)) {
            p.st.close(); var frozen = p.s.local();
            p.s.update(1_000_000_000L); p.s.handle(GameCommand.HARD_DROP);
            assertTrue(p.s.failure().isPresent()); assertEquals(frozen, p.s.local());
        }
    }
}
