package team.tetris.application.network;

import java.util.List;
import java.util.Objects;
import team.tetris.application.model.Difficulty;

/** 통신용 불변 값. 엔진 객체나 Java 직렬화를 전송하지 않는다. 필드·상태 계약은 docs/network-protocol.md. */
public sealed interface MatchMessage {
    int PROTOCOL_VERSION = 1;

    enum Role { SERVER, CLIENT }
    enum Mode { NORMAL, ITEM, TIMED }
    enum Outcome { SERVER_WINS, CLIENT_WINS, DRAW }
    enum EndReason { TOP_OUT, TIME_LIMIT, LEFT }

    record Hello(int version, Role role) implements MatchMessage {
        public Hello { Objects.requireNonNull(role); }
    }

    record Lobby(long revision, Mode mode, Difficulty difficulty, int timeLimitSeconds) implements MatchMessage {
        public Lobby {
            positive(revision, "revision");
            Objects.requireNonNull(mode);
            Objects.requireNonNull(difficulty);
            if (mode == Mode.TIMED ? timeLimitSeconds != 60 && timeLimitSeconds != 120 && timeLimitSeconds != 180
                    : timeLimitSeconds != 0) throw new IllegalArgumentException("Invalid time limit");
        }
    }

    record Ready(long revision, boolean ready) implements MatchMessage {
        public Ready { positive(revision, "revision"); }
    }

    record Start(long revision, long pieceSeed) implements MatchMessage {
        public Start { positive(revision, "revision"); }
    }

    /** sender의 화면과 논리적 패배. 남은 시간은 서버만 0 이상, 클라이언트는 -1. */
    record State(PlayerState player, long remainingNanos) implements MatchMessage {
        public State {
            Objects.requireNonNull(player);
            if (remainingNanos < -1) throw new IllegalArgumentException("Invalid remaining time");
        }
    }

    /** 각 행의 bit x가 x열의 공격 칸. 위에서 아래 순서, 아이템 속성 없음. */
    record Attack(long eventId, List<Integer> rows) implements MatchMessage {
        public Attack {
            positive(eventId, "eventId");
            rows = PlayerState.attackRows(rows);
            if (rows.size() < 2) throw new IllegalArgumentException("Attack requires at least two rows");
        }
    }

    /** request=true이면 요청, false이면 서버가 확정한 정지. owner가 재개 권한을 갖는다. */
    record Pause(Role owner, boolean request) implements MatchMessage {
        public Pause { Objects.requireNonNull(owner); }
    }
    record Resume(boolean request) implements MatchMessage {}
    record Result(Outcome outcome, EndReason reason) implements MatchMessage {
        public Result { Objects.requireNonNull(outcome); Objects.requireNonNull(reason); }
    }
    record Leave() implements MatchMessage {}
    record Ping(long id) implements MatchMessage {
        public Ping { positive(id, "ping id"); }
    }
    record Pong(long id) implements MatchMessage {
        public Pong { positive(id, "ping id"); }
    }

    private static void positive(long value, String name) {
        if (value <= 0) throw new IllegalArgumentException(name + " must be positive");
    }
}
