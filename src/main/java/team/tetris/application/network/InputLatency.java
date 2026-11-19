package team.tetris.application.network;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.LongSupplier;
import team.tetris.application.GameCommand;
import team.tetris.application.port.DebugLogSink;

/** 디버그 전용 입력→로컬 paint / 상대 paint 확인 왕복 표본. 누락도 로그에 남긴다. */
public final class InputLatency {
    private record Sample(GameCommand command, long start) {}
    private final LongSupplier clock;
    private final DebugLogSink log;
    private final String context;
    private final Map<Long, Sample> local = new LinkedHashMap<>(), remote = new LinkedHashMap<>();
    private long sequence;

    public InputLatency(LongSupplier clock, DebugLogSink log, String context) {
        this.clock = clock; this.log = log; this.context = context;
    }
    public long begin(GameCommand command, long receivedNanos) {
        if (remote.size() >= 512) {
            missing(remote, "REMOTE"); missing(local, "LOCAL");
        }
        long id = ++sequence;
        var sample = new Sample(command, receivedNanos);
        local.put(id, sample); remote.put(id, sample);
        return id;
    }
    public void localPainted(long id) { painted(local, id, "LOCAL"); }
    public void remotePainted(long id) { painted(remote, id, "REMOTE_ACK"); }
    public long now() { return clock.getAsLong(); }
    private void painted(Map<Long, Sample> samples, long id, String target) {
        Sample sample = samples.remove(id);
        if (sample == null) return;
        long elapsed = Math.max(0, clock.getAsLong() - sample.start());
        log.append("P2P_LATENCY," + context + "," + id + "," + sample.command() + "," + target + "," + elapsed + "\n");
    }
    public void finish() { missing(local, "LOCAL"); missing(remote, "REMOTE"); }
    private void missing(Map<Long, Sample> samples, String target) {
        samples.forEach((id, sample) -> log.append("P2P_LATENCY," + context + "," + id + "," + sample.command()
                + "," + target + "_MISSING,-1\n"));
        samples.clear();
    }
}
