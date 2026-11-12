package team.tetris.application.network;

import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import team.tetris.application.GameCommand;
import team.tetris.application.network.MatchMessage.Mode;
import team.tetris.application.port.DebugLogSink;
import static org.junit.jupiter.api.Assertions.*;

class InputLatencyTest {
    @Test void onlyPaintAcknowledgesCapturedInputAndDuplicatesDoNotCreateSamples() {
        var clock = new AtomicLong(100);
        var log = new StringBuilder();
        DebugLogSink sink = new DebugLogSink() {
            public void append(String entry) { log.append(entry); }
            public String description() { return "memory"; }
        };
        try (var p = new P2pMatchTestSupport.Pair(Mode.NORMAL, null, null)) {
            p.s.measureLatency(new InputLatency(clock::get, sink, "NORMAL,SERVER,1"));
            p.s.handle(GameCommand.MOVE_LEFT); p.pump();
            long captured = p.s.inputId();
            assertEquals(1, captured);
            assertTrue(log.isEmpty()); // 수신·render만으로 표본을 성공 처리하지 않는다.
            clock.set(200);
            p.s.painted(captured, 0);
            assertTrue(log.toString().contains("MOVE_LEFT,LOCAL,100"));
            p.c.painted(0, p.c.remoteInputId());
            assertFalse(log.toString().contains("REMOTE_ACK"));
            clock.set(300); p.s.update(0);
            assertTrue(log.toString().contains("MOVE_LEFT,REMOTE_ACK,200"));
            String before = log.toString();
            p.c.painted(0, captured); p.s.painted(captured, 0); p.s.update(0);
            assertEquals(before, log.toString());
            p.s.handle(GameCommand.MOVE_RIGHT);
            p.s.finishMeasurement();
            assertTrue(log.toString().contains("MOVE_RIGHT,LOCAL_MISSING,-1"));
            assertTrue(log.toString().contains("MOVE_RIGHT,REMOTE_MISSING,-1"));
        }
    }
}
