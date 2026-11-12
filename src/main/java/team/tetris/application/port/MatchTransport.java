package team.tetris.application.port;

import java.util.Optional;
import java.util.OptionalLong;
import team.tetris.application.network.MatchEnvelope;

/**
 * 한 연결의 비동기 통신 포트. listen/connect/send는 소켓 I/O를 기다리지 않는다.
 * 수신은 poll로 게임 실행 스레드가 가져간다. 어댑터는 세션/UI 콜백을 호출하지 않는다.
 * 종료 후 재접속은 새 인스턴스를 사용한다. 상세 실행 흐름은 docs/network-protocol.md.
 */
public interface MatchTransport extends AutoCloseable {
    enum State { NEW, LISTENING, CONNECTING, HANDSHAKING, CONNECTED, CLOSED, FAILED }
    record Status(State state, String detail, int localPort) {}

    void listen(int port);
    void connect(String host, int port);
    Status status();

    /** 게임 실행 스레드가 순번을 부여하고 MatchProtocol로 검증한 봉투. 연결 전/종료 후에는 예외. */
    void send(MatchEnvelope envelope);
    Optional<MatchEnvelope> poll();

    /** 로컬 단조 시계로 잰 최근 ping RTT. 입력→화면 지연 측정과는 다르다. */
    OptionalLong roundTripNanos();

    @Override
    void close();
}
