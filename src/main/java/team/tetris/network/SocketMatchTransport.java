package team.tetris.network;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import team.tetris.application.network.MatchEnvelope;
import team.tetris.application.network.MatchMessage;
import team.tetris.application.network.MatchMessage.*;
import team.tetris.application.port.MatchTransport;

/** TCP 단일 peer 어댑터. 읽기/쓰기/heartbeat 모두 작업 스레드에서 실행하고 큐는 유한하다. */
public final class SocketMatchTransport implements MatchTransport {
    private static final int TIMEOUT_MILLIS = 3_000;
    private static final int QUEUE_CAPACITY = 256;
    private final MessageCodec codec = new MessageCodec();
    private final int version;
    private final ExecutorService workers = Executors.newThreadPerTaskExecutor(
            Thread.ofVirtual().name("tetris-network-", 0).factory());
    private final ArrayBlockingQueue<MatchEnvelope> incoming = new ArrayBlockingQueue<>(QUEUE_CAPACITY);
    private final ArrayBlockingQueue<MatchEnvelope> outgoing = new ArrayBlockingQueue<>(QUEUE_CAPACITY);
    private final PingTracker ping = new PingTracker(System::nanoTime);
    private volatile Status status = new Status(State.NEW, "", 0);
    private ServerSocket listener;
    private Socket socket;

    public SocketMatchTransport() { this(MatchMessage.PROTOCOL_VERSION); }

    /** 다른 프로토콜 구현과의 호환성 검증용. */
    SocketMatchTransport(int version) { this.version = version; }

    @Override
    public synchronized void listen(int port) {
        checkPort(port, true);
        begin(State.LISTENING);
        workers.execute(() -> accept(port));
    }

    @Override
    public synchronized void connect(String host, int port) {
        if (Objects.requireNonNull(host).isBlank()) throw new IllegalArgumentException("Host is blank");
        checkPort(port, false);
        begin(State.CONNECTING);
        workers.execute(() -> connectPeer(host, port));
    }

    private void begin(State state) {
        if (status.state() != State.NEW) throw new IllegalStateException("Create a new transport for each connection");
        status = new Status(state, "", 0);
    }

    private void accept(int port) {
        try (ServerSocket server = new ServerSocket()) {
            synchronized (this) {
                if (terminal()) return;
                listener = server;
            }
            server.setReuseAddress(true);
            server.bind(new InetSocketAddress(port));
            synchronized (this) {
                if (terminal()) return;
                status = new Status(State.LISTENING, "", server.getLocalPort());
            }
            Socket peer = server.accept();
            server.close(); // 한 peer만 받으며 연결 중에는 수신 대기 포트를 유지하지 않는다.
            establish(peer, Role.SERVER);
        } catch (IOException | RuntimeException e) {
            fail(e);
        }
    }

    private void connectPeer(String host, int port) {
        try {
            Socket peer = new Socket();
            synchronized (this) {
                if (terminal()) { peer.close(); return; }
                socket = peer; // connect 중 close로 취소할 수 있도록 먼저 등록.
            }
            peer.connect(new InetSocketAddress(host, port), TIMEOUT_MILLIS);
            establish(peer, Role.CLIENT);
        } catch (IOException | RuntimeException e) {
            fail(e);
        }
    }

    private void establish(Socket peer, Role role) throws IOException {
        synchronized (this) {
            if (terminal()) { peer.close(); return; }
            socket = peer;
            status = new Status(State.HANDSHAKING, "", peer.getLocalPort());
        }
        peer.setTcpNoDelay(true);
        peer.setSoTimeout(TIMEOUT_MILLIS);
        var in = new DataInputStream(new BufferedInputStream(peer.getInputStream()));
        var out = new DataOutputStream(new BufferedOutputStream(peer.getOutputStream()));
        codec.write(out, new MatchEnvelope(0, 1, new Hello(version, role)));
        MatchEnvelope first = codec.read(in);
        if (first.matchId() != 0 || !(first.message() instanceof Hello hello))
            throw new IOException("Expected HELLO before game messages");
        if (hello.version() != version) throw new IOException("Incompatible protocol version: " + hello.version());
        if (hello.role() == role) throw new IOException("Peer must have the opposite role");
        peer.setSoTimeout(0);
        synchronized (this) {
            if (terminal()) return;
            status = new Status(State.CONNECTED, "", peer.getLocalPort());
            workers.execute(() -> writeLoop(out));
            workers.execute(this::heartbeatLoop);
        }
        readLoop(in);
    }

    private void readLoop(DataInputStream in) throws IOException {
        while (!terminal()) {
            MatchEnvelope envelope = codec.read(in);
            MatchMessage message = envelope.message();
            if (message instanceof Hello) throw new IOException("Repeated HELLO");
            if (message instanceof Ping || message instanceof Pong) {
                if (envelope.matchId() != 0) throw new IOException("Heartbeat must use match 0");
                if (message instanceof Ping request) enqueue(new MatchEnvelope(0, request.id(), new Pong(request.id())));
                else ping.received(((Pong) message).id());
            } else {
                synchronized (this) {
                    if (terminal()) return;
                    if (!incoming.offer(envelope)) throw new IOException("Receive queue overflow");
                }
            }
        }
    }

    private void writeLoop(DataOutputStream out) {
        try {
            while (!terminal()) {
                MatchEnvelope envelope = outgoing.take();
                if (envelope.message() instanceof Ping request) ping.sent(request.id());
                codec.write(out, envelope);
            }
        } catch (IOException | RuntimeException e) {
            fail(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void heartbeatLoop() {
        long id = 1;
        try {
            while (!terminal()) {
                enqueue(new MatchEnvelope(0, id, new Ping(id)));
                id++;
                Thread.sleep(1_000);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @Override
    public synchronized void send(MatchEnvelope envelope) {
        Objects.requireNonNull(envelope);
        if (envelope.message() instanceof Hello || envelope.message() instanceof Ping || envelope.message() instanceof Pong)
            throw new IllegalArgumentException("Handshake and heartbeat belong to the transport");
        if (status.state() != State.CONNECTED) throw new IllegalStateException("Transport is not connected");
        enqueue(envelope);
    }

    private synchronized void enqueue(MatchEnvelope envelope) {
        if (terminal()) return;
        if (!outgoing.offer(envelope)) terminate(State.FAILED, "Send queue overflow");
    }

    @Override
    public Optional<MatchEnvelope> poll() { return Optional.ofNullable(incoming.poll()); }

    @Override
    public Status status() { return status; }

    @Override
    public OptionalLong roundTripNanos() { return ping.latest(); }

    private boolean terminal() { return status.state() == State.CLOSED || status.state() == State.FAILED; }

    private synchronized void fail(Exception error) {
        if (!terminal()) terminate(State.FAILED, error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage());
    }

    @Override
    public synchronized void close() {
        if (!terminal()) terminate(State.CLOSED, "");
    }

    private void terminate(State state, String detail) {
        status = new Status(state, detail, status.localPort());
        closeQuietly(socket);
        closeQuietly(listener);
        incoming.clear();
        outgoing.clear();
        workers.shutdownNow();
    }

    private static void closeQuietly(AutoCloseable resource) {
        if (resource != null) {
            try { resource.close(); } catch (Exception ignored) { /* 종료 시 원래 실패 사유를 보존한다. */ }
        }
    }

    private static void checkPort(int port, boolean ephemeralAllowed) {
        if (port < (ephemeralAllowed ? 0 : 1) || port > 65_535) throw new IllegalArgumentException("Invalid port");
    }
}
