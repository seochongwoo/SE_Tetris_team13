package team.tetris.application.network;

import java.util.EnumMap;
import java.util.Map;
import team.tetris.application.network.MatchMessage.*;

/**
 * 게임 실행 스레드 전용 상태 검사기. 로컬 송신 전과 원격 수신 후 같은 accept를 호출한다.
 * ACCEPTED인 메시지만 세션/화면에 적용한다. 네트워크 스레드는 이 객체를 만지지 않는다.
 */
public final class MatchProtocol {
    public enum Phase { LOBBY, PLAYING, PAUSED, RESULT }
    public enum Decision { ACCEPTED, WRONG_STATE, WRONG_SENDER, STALE_MATCH, DUPLICATE, STALE_CONFIGURATION }

    private final Map<Role, Long> sequences = new EnumMap<>(Role.class);
    private final Map<Role, Long> attacks = new EnumMap<>(Role.class);
    private final Map<Role, Boolean> ready = new EnumMap<>(Role.class);
    private Phase phase = Phase.LOBBY;
    private long matchId;
    private long revision;
    private Role pauseOwner;

    public Phase phase() { return phase; }
    public long matchId() { return matchId; }

    public Decision accept(Role sender, MatchEnvelope envelope) {
        java.util.Objects.requireNonNull(sender);
        MatchMessage message = envelope.message();
        if (message instanceof Hello || message instanceof Ping || message instanceof Pong)
            return Decision.WRONG_STATE; // 핸드셰이크/heartbeat는 어댑터가 소비한다.
        boolean lobby = message instanceof Lobby || message instanceof Ready;
        if (lobby ? envelope.matchId() != 0
                : message instanceof Start ? envelope.matchId() <= matchId
                : envelope.matchId() != (phase == Phase.LOBBY ? 0 : matchId))
            return Decision.STALE_MATCH;
        if (envelope.sequence() <= sequences.getOrDefault(sender, 0L)) return Decision.DUPLICATE;

        Decision decision = validate(sender, message);
        if (decision != Decision.ACCEPTED) return decision;
        sequences.put(sender, envelope.sequence());
        switch (message) {
            case Lobby config -> {
                revision = config.revision();
                phase = Phase.LOBBY;
                ready.clear();
                pauseOwner = null;
            }
            case Ready value -> ready.put(sender, value.ready());
            case Start ignored -> {
                matchId = envelope.matchId();
                phase = Phase.PLAYING;
                ready.clear();
                attacks.clear();
            }
            case Attack attack -> attacks.put(sender, attack.eventId());
            case Pause pause -> {
                if (!pause.request()) { phase = Phase.PAUSED; pauseOwner = pause.owner(); }
            }
            case Resume resume -> {
                if (!resume.request()) { phase = Phase.PLAYING; pauseOwner = null; }
            }
            case Result ignored -> phase = Phase.RESULT;
            case Leave ignored -> ready.put(sender, false);
            default -> { }
        }
        return Decision.ACCEPTED;
    }

    private Decision validate(Role sender, MatchMessage message) {
        return switch (message) {
            case Lobby config -> sender != Role.SERVER ? Decision.WRONG_SENDER
                    : phase != Phase.LOBBY && phase != Phase.RESULT ? Decision.WRONG_STATE
                    : config.revision() <= revision ? Decision.STALE_CONFIGURATION : Decision.ACCEPTED;
            case Ready value -> phase != Phase.LOBBY ? Decision.WRONG_STATE
                    : value.revision() != revision ? Decision.STALE_CONFIGURATION : Decision.ACCEPTED;
            case Start start -> sender != Role.SERVER ? Decision.WRONG_SENDER
                    : phase != Phase.LOBBY || !ready.getOrDefault(Role.SERVER, false)
                        || !ready.getOrDefault(Role.CLIENT, false) ? Decision.WRONG_STATE
                    : start.revision() != revision ? Decision.STALE_CONFIGURATION : Decision.ACCEPTED;
            case State state -> !inMatch() ? Decision.WRONG_STATE
                    : (sender == Role.SERVER ? state.remainingNanos() < 0 : state.remainingNanos() != -1)
                        ? Decision.WRONG_SENDER : Decision.ACCEPTED;
            case Attack attack -> !inMatch() ? Decision.WRONG_STATE
                    : attack.eventId() <= attacks.getOrDefault(sender, 0L) ? Decision.DUPLICATE : Decision.ACCEPTED;
            case Pause pause -> phase != Phase.PLAYING ? Decision.WRONG_STATE
                    : (pause.request() ? pause.owner() != sender : sender != Role.SERVER)
                        ? Decision.WRONG_SENDER : Decision.ACCEPTED;
            case Resume resume -> phase != Phase.PAUSED ? Decision.WRONG_STATE
                    : (resume.request() ? sender != pauseOwner : sender != Role.SERVER)
                        ? Decision.WRONG_SENDER : Decision.ACCEPTED;
            case Result ignored -> sender != Role.SERVER ? Decision.WRONG_SENDER
                    : !inMatch() ? Decision.WRONG_STATE : Decision.ACCEPTED;
            case Leave ignored -> Decision.ACCEPTED;
            default -> Decision.WRONG_STATE;
        };
    }

    private boolean inMatch() { return phase == Phase.PLAYING || phase == Phase.PAUSED; }
}
