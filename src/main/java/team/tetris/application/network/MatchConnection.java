package team.tetris.application.network;

import java.util.Optional;
import team.tetris.application.network.MatchMessage.Role;
import team.tetris.application.port.MatchTransport;

/** 로비에서 경기로 넘겨도 프로토콜과 송신 순번을 유지하는 실행 스레드 전용 연결. */
public final class MatchConnection implements AutoCloseable {
    private final MatchTransport transport;
    private final Role role;
    private final MatchProtocol protocol = new MatchProtocol();
    private long sequence;

    public MatchConnection(MatchTransport transport, Role role) {
        this.transport = java.util.Objects.requireNonNull(transport);
        this.role = java.util.Objects.requireNonNull(role);
    }
    public MatchTransport transport() { return transport; }
    public MatchProtocol protocol() { return protocol; }
    public Role role() { return role; }

    public void send(long matchId, MatchMessage message) {
        var envelope = new MatchEnvelope(matchId, ++sequence, message);
        if (protocol.accept(role, envelope) != MatchProtocol.Decision.ACCEPTED)
            throw new IllegalStateException("Message rejected by protocol: " + message);
        transport.send(envelope);
    }

    public Optional<MatchEnvelope> receive() {
        Optional<MatchEnvelope> next;
        for (int polled = 0; polled < 256 && (next = transport.poll()).isPresent(); polled++) {
            if (protocol.accept(role == Role.SERVER ? Role.CLIENT : Role.SERVER, next.get())
                    == MatchProtocol.Decision.ACCEPTED) return next;
        }
        return Optional.empty();
    }
    @Override public void close() { transport.close(); }
}
