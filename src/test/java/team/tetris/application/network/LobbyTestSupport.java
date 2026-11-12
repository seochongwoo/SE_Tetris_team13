package team.tetris.application.network;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import team.tetris.application.port.MatchTransport;
import team.tetris.application.port.RecentIpRepository;

public final class LobbyTestSupport {
    private LobbyTestSupport() {}
    public static final class MemoryIps implements RecentIpRepository {
        public List<String> values = List.of();
        public int writes;
        public List<String> load() { return values; }
        public void save(List<String> addresses) { values = List.copyOf(addresses); writes++; }
    }
    public static final class Transport implements MatchTransport {
        public Status status = new Status(State.NEW, "", 0);
        public final ArrayDeque<MatchEnvelope> incoming = new ArrayDeque<>();
        public final List<MatchEnvelope> sent = new ArrayList<>();
        public Transport peer;
        public boolean closed;
        public String host;
        public void listen(int port) { status = new Status(State.LISTENING, "", port); }
        public void connect(String host, int port) { this.host = host; status = new Status(State.CONNECTING, "", port); }
        public Status status() { return status; }
        public void connected() { status = new Status(State.CONNECTED, "", 21313); }
        public void send(MatchEnvelope envelope) {
            sent.add(envelope);
            if (peer != null) peer.incoming.add(envelope);
        }
        public Optional<MatchEnvelope> poll() { return Optional.ofNullable(incoming.poll()); }
        public OptionalLong roundTripNanos() { return OptionalLong.empty(); }
        public void close() { closed = true; status = new Status(State.CLOSED, "", 0); }
    }
}
