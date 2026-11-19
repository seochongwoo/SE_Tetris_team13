package team.tetris.application.network;

import static org.junit.jupiter.api.Assertions.*;
import static team.tetris.application.network.MatchMessage.*;
import static team.tetris.application.network.MatchProtocol.Decision.*;

import java.util.List;
import org.junit.jupiter.api.Test;
import team.tetris.application.model.Difficulty;

class MatchProtocolTest {
    private final MatchProtocol protocol = new MatchProtocol();
    private long sequence;

    private MatchProtocol.Decision apply(Role role, long match, MatchMessage message) {
        return protocol.accept(role, new MatchEnvelope(match, ++sequence, message));
    }

    private void start(long match, long revision) {
        assertEquals(ACCEPTED, apply(Role.SERVER, 0, new Lobby(revision, Mode.NORMAL, Difficulty.NORMAL, 0)));
        assertEquals(ACCEPTED, apply(Role.SERVER, 0, new Ready(revision, true)));
        assertEquals(ACCEPTED, apply(Role.CLIENT, 0, new Ready(revision, true)));
        assertEquals(ACCEPTED, apply(Role.SERVER, match, new Start(revision, 42)));
    }

    @Test
    void bothReadyMustReferToTheCurrentConfigurationAndOnlyServerStarts() {
        assertEquals(STALE_CONFIGURATION, apply(Role.CLIENT, 0, new Ready(1, true)));
        assertEquals(WRONG_SENDER, apply(Role.CLIENT, 0, new Lobby(1, Mode.ITEM, Difficulty.HARD, 0)));
        assertEquals(ACCEPTED, apply(Role.SERVER, 0, new Lobby(1, Mode.ITEM, Difficulty.HARD, 0)));
        assertEquals(ACCEPTED, apply(Role.SERVER, 0, new Ready(1, true)));
        assertEquals(WRONG_STATE, apply(Role.SERVER, 1, new Start(1, 7)));
        assertEquals(ACCEPTED, apply(Role.CLIENT, 0, new Ready(1, true)));
        assertEquals(ACCEPTED, apply(Role.SERVER, 0, new Lobby(2, Mode.TIMED, Difficulty.EASY, 120)));
        assertEquals(STALE_CONFIGURATION, apply(Role.CLIENT, 0, new Ready(1, true)));
        assertEquals(WRONG_STATE, apply(Role.SERVER, 1, new Start(2, 7)));
        assertEquals(ACCEPTED, apply(Role.SERVER, 0, new Ready(2, true)));
        assertEquals(ACCEPTED, apply(Role.CLIENT, 0, new Ready(2, true)));
        assertEquals(WRONG_SENDER, apply(Role.CLIENT, 1, new Start(2, 7)));
        assertEquals(ACCEPTED, apply(Role.SERVER, 1, new Start(2, 7)));
        assertEquals(STALE_MATCH, apply(Role.SERVER, 1, new Start(2, 7)));
    }

    @Test
    void rejectsWrongStateOldMatchAndDuplicateAttackEvenWithANewSequence() {
        var attack = new Attack(1, List.of(1022, 1007));
        assertEquals(WRONG_STATE, apply(Role.CLIENT, 0, attack));
        start(1, 1);
        assertEquals(ACCEPTED, apply(Role.CLIENT, 1, attack));
        assertEquals(DUPLICATE, apply(Role.CLIENT, 1, attack));
        assertEquals(ACCEPTED, apply(Role.SERVER, 1, attack)); // 송신자별 ID
        assertEquals(ACCEPTED, apply(Role.SERVER, 1, new Result(Outcome.DRAW, EndReason.TOP_OUT)));
        assertEquals(WRONG_STATE, apply(Role.CLIENT, 1, new Attack(2, attack.rows())));
        start(2, 2);
        assertEquals(STALE_MATCH, apply(Role.CLIENT, 1, new Attack(3, attack.rows())));
        assertEquals(ACCEPTED, apply(Role.CLIENT, 2, attack)); // 경기마다 ID를 다시 시작할 수 있다.
        assertEquals(2, protocol.matchId());
    }

    @Test
    void replayedEnvelopeCannotApplyTwiceAndRejectedMessagesDoNotPoisonSequence() {
        start(1, 1);
        long next = ++sequence;
        var envelope = new MatchEnvelope(1, next, new Attack(7, List.of(3, 5)));
        assertEquals(ACCEPTED, protocol.accept(Role.CLIENT, envelope));
        assertEquals(DUPLICATE, protocol.accept(Role.CLIENT, envelope));
        assertEquals(STALE_MATCH, protocol.accept(Role.CLIENT,
                new MatchEnvelope(99, Long.MAX_VALUE, new Attack(8, List.of(3, 5)))));
        assertEquals(ACCEPTED, apply(Role.CLIENT, 1, new Attack(8, List.of(3, 5))));
    }

    @Test
    void pauseAndResumeAreServerConfirmedAndOnlyOwnerMayRequestResume() {
        start(1, 1);
        assertEquals(ACCEPTED, apply(Role.CLIENT, 1, new Pause(Role.CLIENT, true)));
        assertEquals(MatchProtocol.Phase.PLAYING, protocol.phase());
        assertEquals(WRONG_SENDER, apply(Role.CLIENT, 1, new Pause(Role.CLIENT, false)));
        assertEquals(ACCEPTED, apply(Role.SERVER, 1, new Pause(Role.CLIENT, false)));
        assertEquals(MatchProtocol.Phase.PAUSED, protocol.phase());
        assertEquals(WRONG_SENDER, apply(Role.SERVER, 1, new Resume(true)));
        assertEquals(ACCEPTED, apply(Role.CLIENT, 1, new Resume(true)));
        assertEquals(MatchProtocol.Phase.PAUSED, protocol.phase());
        assertEquals(WRONG_SENDER, apply(Role.CLIENT, 1, new Resume(false)));
        assertEquals(ACCEPTED, apply(Role.SERVER, 1, new Resume(false)));
        assertEquals(MatchProtocol.Phase.PLAYING, protocol.phase());
        assertEquals(WRONG_STATE, apply(Role.CLIENT, 1, new Resume(true)));
    }

    @Test
    void leavingWhilePausedStillAllowsServerResultAndLobbyReturn() {
        start(1, 1);
        assertEquals(ACCEPTED, apply(Role.SERVER, 1, new Pause(Role.SERVER, false)));
        assertEquals(ACCEPTED, apply(Role.CLIENT, 1, new Leave()));
        assertEquals(WRONG_SENDER, apply(Role.CLIENT, 1, new Result(Outcome.CLIENT_WINS, EndReason.LEFT)));
        assertEquals(ACCEPTED, apply(Role.SERVER, 1, new Result(Outcome.SERVER_WINS, EndReason.LEFT)));
        assertEquals(WRONG_STATE, apply(Role.SERVER, 1, new Result(Outcome.DRAW, EndReason.TIME_LIMIT)));
        assertEquals(ACCEPTED, apply(Role.SERVER, 0, new Lobby(2, Mode.NORMAL, Difficulty.NORMAL, 0)));
        assertEquals(MatchProtocol.Phase.LOBBY, protocol.phase());
        assertEquals(WRONG_STATE, apply(Role.SERVER, 2, new Start(2, 8)));
    }
}
