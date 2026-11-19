package team.tetris.application.network;

import java.util.Optional;
import java.util.function.Function;
import team.tetris.application.*;
import team.tetris.application.model.GameMode;
import team.tetris.application.model.Difficulty;
import team.tetris.application.network.MatchMessage.*;
import team.tetris.application.port.MatchTransport;
import team.tetris.core.EnginePhase;
import team.tetris.core.port.TetrisEnginePort;
import team.tetris.core.rule.*;

/** 실행 스레드 전용 P2P 경기. 자기 엔진만 실행하며 서버가 시간·정지·결과를 확정한다. */
public final class P2pMatch {
    private final P2pLobby.StartedMatch started;
    private final MatchConnection connection;
    private final AttackQueue incoming = new AttackQueue(10);
    private final SinglePlayerSession local;
    private PlayerState remote;
    private Role pauseOwner;
    private Result result;
    private long remaining;
    private long attackId;
    private boolean leaving;
    private String failure;
    private InputLatency latency;
    private long inputId, remoteInputId, acknowledgedInputId;

    public void measureLatency(InputLatency latency) { this.latency = latency; }
    public long inputId() { return inputId; }
    public long remoteInputId() { return remoteInputId; }
    public void painted(long localId, long remoteId) {
        // 결과 화면은 서버 확정 스냅샷이므로 직전 로컬 입력 ID의 표시 증거로 쓰지 않는다.
        if (result != null || failure != null) return;
        if (latency != null) latency.localPainted(localId);
        if (remoteId > acknowledgedInputId) {
            send(new Painted(remoteId));
            acknowledgedInputId = remoteId;
        }
    }
    public void finishMeasurement() { if (latency != null) latency.finish(); }

    public P2pMatch(P2pLobby.StartedMatch started, Function<IncomingLines, TetrisEnginePort> engines,
                    ScoreRule scoring, SpeedRule speed) {
        this.started = started;
        connection = started.connection();
        remaining = started.configuration().timeLimitSeconds() * 1_000_000_000L;
        local = new SinglePlayerSession(engines.apply(incoming), scoring, speed,
                started.configuration().mode() == Mode.ITEM ? GameMode.ITEM : GameMode.NORMAL,
                started.configuration().difficulty());
        local.observeSteps(step -> {
            if (result != null || step.snapshot().phase() == EnginePhase.GAME_OVER) return;
            var rows = PlayerStates.masks(AttackRule.linesFor(step.lockResult(), step.clearResult()));
            if (!rows.isEmpty()) send(new Attack(++attackId, rows));
        });
        publish();
    }

    public Role role() { return connection.role(); }
    public Mode mode() { return started.configuration().mode(); }
    public Difficulty difficulty() { return started.configuration().difficulty(); }
    public long matchId() { return started.matchId(); }
    public long remainingNanos() { return remaining; }
    public boolean peerDisconnected() { return connection.transport().status().peerDisconnected(); }
    public boolean paused() { return pauseOwner != null; }
    public Role pauseOwner() { return pauseOwner; }
    public Optional<Result> result() { return Optional.ofNullable(result); }
    public Optional<String> failure() { return Optional.ofNullable(failure); }
    public boolean lagging() { return failure == null && connection.transport().lagging(); }
    public PlayerState local() {
        if (result != null && result.server() != null) return role() == Role.SERVER ? result.server() : result.client();
        return PlayerStates.from(local.snapshot(), incoming.pending(), result != null || defeated());
    }
    public Optional<PlayerState> remote() { return Optional.ofNullable(remote); }

    public void handle(GameCommand command) {
        long receivedNanos = latency == null ? 0 : latency.now();
        receive();
        decide();
        if (result != null || failure != null || leaving) return;
        switch (command) {
            case QUIT_GAME -> {
                leaving = true;
                send(new Leave());
                if (role() == Role.SERVER) finish(Outcome.CLIENT_WINS, EndReason.LEFT);
            }
            case PAUSE -> {
                if (!paused()) {
                    if (role() == Role.SERVER) pause(role());
                    else send(new Pause(role(), true));
                }
            }
            case RESUME -> {
                if (pauseOwner == role()) {
                    if (role() == Role.SERVER) resume();
                    else send(new Resume(true));
                }
            }
            default -> {
                if (paused() || defeated() || (mode() == Mode.TIMED && remaining == 0)) return;
                if (local.snapshot().lineClearAnimation() != null) return;
                apply(command, receivedNanos);
                publish();
                decide();
            }
        }
    }

    public void update(long elapsedNanos) {
        if (elapsedNanos < 0) throw new IllegalArgumentException("Negative elapsed time");
        boolean wasPaused = paused();
        receive();
        if (result != null || failure != null) return;
        // 재개 메시지를 받기 전 정지 상태에서 흐른 시간은 진행에 포함하지 않는다.
        if (!paused() && !wasPaused && !leaving) {
            long elapsed = mode() == Mode.TIMED ? Math.min(elapsedNanos, remaining) : elapsedNanos;
            if (!defeated()) local.update(elapsed);
            if (role() == Role.SERVER && mode() == Mode.TIMED) remaining -= elapsed;
        }
        decide();
        if (result == null) publish();
    }

    private void receive() {
        if (failure != null) return;
        var status = connection.transport().status();
        if (status.peerDisconnected()) {
            if (result != null) return;
            boolean serverWins = (role() == Role.SERVER) != leaving;
            result = new Result(serverWins ? Outcome.SERVER_WINS : Outcome.CLIENT_WINS, EndReason.LEFT);
            return;
        }
        if (status.state() == MatchTransport.State.FAILED || status.state() == MatchTransport.State.CLOSED) {
            fail(status.detail().isBlank() ? "상대 연결이 종료되었습니다" : status.detail());
            return;
        }
        if (result != null) return; // 연결 감시는 결과 연출 중에도 유지하되 다음 LOBBY는 로비가 읽는다.
        // 한 프레임의 작업량 제한. 거부 메시지도 MatchConnection에서 제한한다.
        for (int count = 0; count < 256 && result == null; count++) {
            var next = connection.receive();
            if (next.isEmpty()) break;
            switch (next.get().message()) {
                case State state -> {
                    remote = state.player();
                    remoteInputId = state.inputId();
                    if (role() == Role.CLIENT) remaining = state.remainingNanos();
                }
                case Attack attack -> incoming.add(PlayerStates.rows(attack.rows()));
                case Painted painted -> { if (latency != null) latency.remotePainted(painted.inputId()); }
                case Pause pause -> {
                    if (pause.request()) pause(pause.owner());
                    else applyPause(pause.owner());
                }
                case Resume resume -> {
                    if (resume.request()) resume();
                    else applyResume();
                }
                case Result confirmed -> {
                    result = confirmed;
                    if (confirmed.server() != null) remote = confirmed.server();
                }
                case Leave ignored -> {
                    if (role() == Role.SERVER) finish(Outcome.SERVER_WINS, EndReason.LEFT);
                }
                default -> { }
            }
        }
        // 프레임의 로컬 패배와 함께 판정할 수 있도록 update에서 decide한다.
    }

    private boolean defeated() { return local.snapshot().engine().phase() == EnginePhase.GAME_OVER; }

    private void apply(GameCommand command, long receivedNanos) {
        PlayerState before = latency == null ? null : local();
        local.handle(command);
        if (latency != null && !local().equals(before)) inputId = latency.begin(command, receivedNanos);
    }

    private void decide() {
        if (role() != Role.SERVER || result != null || failure != null) return;
        boolean otherLost = remote != null && remote.defeated();
        if (defeated() || otherLost) finish(defeated() && otherLost ? Outcome.DRAW
                : defeated() ? Outcome.CLIENT_WINS : Outcome.SERVER_WINS, EndReason.TOP_OUT);
        else if (mode() == Mode.TIMED && remaining == 0 && remote != null) {
            int comparison = Long.compare(local.snapshot().score(), remote.score());
            finish(comparison == 0 ? Outcome.DRAW : comparison > 0 ? Outcome.SERVER_WINS : Outcome.CLIENT_WINS, EndReason.TIME_LIMIT);
        }
    }

    private void finish(Outcome outcome, EndReason reason) {
        result = remote == null ? new Result(outcome, reason) : new Result(outcome, reason,
                PlayerStates.from(local.snapshot(), incoming.pending(), true), remote);
        publish(); // 연출을 제거한 확정 화면을 RESULT 앞에 보낸다.
        send(result);
    }
    private void pause(Role owner) { send(new Pause(owner, false)); applyPause(owner); publish(); }
    private void applyPause(Role owner) { pauseOwner = owner; local.handle(GameCommand.PAUSE); }
    private void resume() { send(new Resume(false)); applyResume(); }
    private void applyResume() { pauseOwner = null; local.handle(GameCommand.RESUME); }
    private void publish() { send(new State(local(), role() == Role.SERVER ? remaining : -1, inputId)); }
    private void send(MatchMessage message) {
        if (failure != null) return;
        try { connection.send(started.matchId(), message); }
        catch (RuntimeException error) { if (!peerDisconnected()) fail("전송 실패: " + error.getMessage()); }
    }

    private void fail(String reason) {
        failure = reason;
        connection.close();
    }
}
