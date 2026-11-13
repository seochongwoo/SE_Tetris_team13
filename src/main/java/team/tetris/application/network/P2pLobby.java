package team.tetris.application.network;

import java.util.List;
import java.util.Optional;
import java.util.function.LongSupplier;
import java.util.function.Supplier;
import team.tetris.application.model.Difficulty;
import team.tetris.application.network.MatchMessage.*;
import team.tetris.application.port.MatchTransport;

/** 로비 규칙. update와 조작은 UI/게임 실행 스레드에서만 호출한다. */
public final class P2pLobby implements AutoCloseable {
    public static final int PORT = 21313;
    public enum Stage { CHOICE, CONNECTING, LOBBY, STARTED, FAILED, CLOSED }
    public record StartedMatch(MatchConnection connection, Lobby configuration, long matchId, long pieceSeed) {}

    private final Supplier<MatchTransport> transports;
    private final Supplier<List<String>> localAddresses;
    private final RecentIps recent;
    private final Difficulty difficulty;
    private final LongSupplier seeds;
    private final int port;
    private MatchConnection connection;
    private Stage stage = Stage.CHOICE;
    private Lobby configuration;
    private boolean localReady;
    private boolean remoteReady;
    private String host;
    private String status;
    private List<String> addresses = List.of();
    private StartedMatch started;

    public P2pLobby(Supplier<MatchTransport> transports, Supplier<List<String>> localAddresses,
                    RecentIps recent, Difficulty difficulty, LongSupplier seeds) {
        this(transports, localAddresses, recent, difficulty, seeds, PORT);
    }

    /** port=0은 localhost 서버 테스트에서만 사용한다. */
    public P2pLobby(Supplier<MatchTransport> transports, Supplier<List<String>> localAddresses,
                    RecentIps recent, Difficulty difficulty, LongSupplier seeds, int port) {
        this.transports = java.util.Objects.requireNonNull(transports);
        this.localAddresses = java.util.Objects.requireNonNull(localAddresses);
        this.recent = java.util.Objects.requireNonNull(recent);
        this.difficulty = java.util.Objects.requireNonNull(difficulty);
        this.seeds = java.util.Objects.requireNonNull(seeds);
        this.port = port;
        status = "서버 또는 클라이언트를 선택하세요";
    }

    public Stage stage() { return stage; }
    public String status() { return status; }
    public List<String> addresses() { return addresses; }
    public RecentIps recent() { return recent; }
    public Optional<Lobby> configuration() { return Optional.ofNullable(configuration); }
    public boolean isServer() { return connection != null && connection.role() == Role.SERVER; }
    public boolean localReady() { return localReady; }
    public boolean remoteReady() { return remoteReady; }
    public int localPort() { return connection == null ? 0 : connection.transport().status().localPort(); }

    public void host() {
        reset();
        addresses = List.copyOf(localAddresses.get());
        connection = new MatchConnection(transports.get(), Role.SERVER);
        stage = Stage.CONNECTING;
        status = "접속 대기 중";
        try { connection.transport().listen(port); }
        catch (RuntimeException error) { fail(error.getMessage()); }
    }

    public void connect(String address) {
        // 잘못된 입력은 기존 연결을 바꾸지 않는다.
        String normalized = Ipv4Address.normalize(address);
        reset();
        host = normalized;
        connection = new MatchConnection(transports.get(), Role.CLIENT);
        stage = Stage.CONNECTING;
        status = "접속 중";
        try { connection.transport().connect(host, port); }
        catch (RuntimeException error) { fail(error.getMessage()); }
    }

    public void update() {
        if (connection == null || stage == Stage.FAILED || stage == Stage.CLOSED) return;
        var transportStatus = connection.transport().status();
        if (transportStatus.state() == MatchTransport.State.FAILED || transportStatus.state() == MatchTransport.State.CLOSED) {
            fail(transportStatus.detail().isBlank() ? "상대 연결이 종료되었습니다" : transportStatus.detail());
            return;
        }
        try {
            if (stage == Stage.CONNECTING) {
                switch (transportStatus.state()) {
                    case HANDSHAKING -> status = "연결 확인 중";
                    case CONNECTED -> {
                        stage = Stage.LOBBY;
                        status = "연결됨 · 서버 설정 대기";
                        if (isServer()) configure(Mode.NORMAL, 0);
                        else recent.connected(host);
                    }
                    default -> { }
                }
            }
            // START 다음의 경기 메시지는 경기 세션에 남겨둔다.
            while (stage == Stage.LOBBY) {
                var envelope = connection.receive();
                if (envelope.isEmpty()) break;
                switch (envelope.get().message()) {
                    case Lobby config -> applyConfiguration(config);
                    case Ready ready -> remoteReady = ready.ready();
                    case Start start -> start(envelope.get().matchId(), start);
                    case Leave ignored -> fail("상대가 로비를 나갔습니다");
                    default -> { }
                }
            }
            maybeStart();
        } catch (RuntimeException error) { fail(error.getMessage()); }
    }

    public void configure(Mode mode, int seconds) {
        if (stage != Stage.LOBBY || !isServer()) return;
        if (configuration != null && configuration.mode() == mode && configuration.timeLimitSeconds() == seconds) return;
        var next = new Lobby(configuration == null ? 1 : configuration.revision() + 1, mode, difficulty, seconds);
        try { connection.send(0, next); applyConfiguration(next); }
        catch (RuntimeException error) { fail(error.getMessage()); }
    }

    private void applyConfiguration(Lobby next) {
        configuration = next;
        localReady = remoteReady = false;
        started = null;
        status = "연결됨 · 양쪽 게임 시작 대기";
    }

    public void ready() {
        if (stage != Stage.LOBBY || configuration == null || localReady) return;
        try {
            connection.send(0, new Ready(configuration.revision(), true));
            localReady = true;
            maybeStart();
        } catch (RuntimeException error) { fail(error.getMessage()); }
    }

    private void maybeStart() {
        if (stage != Stage.LOBBY || !isServer() || !localReady || !remoteReady) return;
        long id = connection.protocol().matchId() + 1;
        var message = new Start(configuration.revision(), seeds.getAsLong());
        connection.send(id, message);
        start(id, message);
    }

    private void start(long id, Start message) {
        stage = Stage.STARTED;
        status = "양쪽 준비 완료 · 경기 시작";
        started = new StartedMatch(connection, configuration, id, message.pieceSeed());
        localReady = remoteReady = false;
    }

    /** 카드 24에서 한 번 가져가 경기 세션을 구성한다. 연결과 순번의 소유권도 이어받는다. */
    public Optional<StartedMatch> takeStartedMatch() {
        var result = Optional.ofNullable(started);
        started = null;
        return result;
    }

    /** 경기 결과를 표시한 뒤 호출. 서버만 새 설정을 보내고 클라이언트는 그 설정을 기다린다. */
    public void returnAfterResult() {
        if (stage != Stage.STARTED || connection.protocol().phase() != MatchProtocol.Phase.RESULT)
            throw new IllegalStateException("A confirmed result is required");
        stage = Stage.LOBBY;
        started = null;
        localReady = remoteReady = false;
        status = "연결됨 · 서버 설정 대기";
        if (isServer()) {
            var next = new Lobby(configuration.revision() + 1, configuration.mode(),
                    configuration.difficulty(), configuration.timeLimitSeconds());
            try { connection.send(0, next); applyConfiguration(next); }
            catch (RuntimeException error) { fail(error.getMessage()); }
        } else configuration = null;
    }

    private void fail(String reason) {
        stage = Stage.FAILED;
        status = "접속 실패: " + reason;
        localReady = remoteReady = false;
        started = null;
        if (connection != null) connection.close();
    }

    public void reset() {
        if (connection != null) connection.close();
        connection = null;
        stage = Stage.CHOICE;
        configuration = null;
        started = null;
        localReady = remoteReady = false;
        addresses = List.of();
        status = "서버 또는 클라이언트를 선택하세요";
    }

    @Override public void close() { reset(); stage = Stage.CLOSED; }
}
