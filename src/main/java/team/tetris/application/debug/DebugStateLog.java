package team.tetris.application.debug;

import java.time.Instant;
import java.util.TreeMap;
import java.util.UUID;
import team.tetris.application.GameSnapshot;
import team.tetris.application.model.Difficulty;
import team.tetris.application.model.GameMode;
import team.tetris.application.port.DebugLogSink;
import team.tetris.core.*;
import team.tetris.core.item.SoloBlock;
import team.tetris.core.result.EngineStep;

/** 한 동작 뒤의 전체 상태. 프레임 렌더링은 기록하지 않는다. */
final class DebugStateLog {
    private final DebugLogSink sink;
    private final UUID id = UUID.randomUUID();
    private final DebugSetup setup;
    private final GameMode mode;
    private long sequence;

    DebugStateLog(DebugLogSink sink, DebugSetup setup, GameMode mode) {
        this.sink = sink;
        this.setup = setup;
        this.mode = mode;
    }

    long sequence() { return sequence; }
    String description() { return sink.description(); }

    void write(String event, GameSnapshot state, Difficulty difficulty, boolean frozen,
               long accumulatedNanos, String sourceState, EngineStep step) {
        var engine = state.engine();
        StringBuilder out = new StringBuilder();
        out.append("\n[DEBUG ").append(Instant.now()).append(" session=").append(id)
                .append(" #").append(++sequence).append("] ").append(event).append('\n');
        out.append("setup=").append(setup).append('\n');
        out.append("mode=").append(mode).append(" difficulty=").append(difficulty)
                .append(" status=").append(state.status()).append(" phase=").append(engine.phase())
                .append(" frozen=").append(frozen).append('\n');
        out.append("score=").append(state.score()).append(" clearedLines=").append(state.clearedLines())
                .append(" level=").append(state.level()).append(" gravityNanos=").append(state.gravityIntervalNanos())
                .append(" accumulatedNanos=").append(accumulatedNanos)
                .append(" doubleScoreClears=").append(state.doubleScoreClears()).append('\n');
        ActivePiece active = engine.activePiece();
        out.append("active=").append(active == null ? "none" : piece(active.piece()) + " origin="
                + active.origin() + " rotation=" + active.rotation() + " cells=" + java.util.Arrays.toString(active.cells()))
                .append('\n');
        out.append("next=").append(piece(engine.nextPiece())).append('\n');
        out.append("source=").append(sourceState).append('\n');
        if (step != null) {
            out.append("drop=").append(step.dropResult()).append(" lock=").append(step.lockResult())
                    .append(" clearRows=").append(step.clearResult() == null ? "[]" : step.clearResult().clearedRows()
                            .stream().map(row -> row.rowIndex()).toList()).append('\n');
            out.append("items=").append(step.itemActivations().stream().map(item -> item.item().getClass().getSimpleName()
                    + "(" + item.item().symbol() + ")@" + item.position()).toList()).append('\n');
        }
        Cell[][] board = engine.board();
        out.append("board (fixed cells; active piece above; #=temporary):\n");
        for (int y = 0; y < board.length; y++) {
            out.append(String.format(java.util.Locale.ROOT, "%02d|", y));
            for (Cell cell : board[y]) {
                out.append(cell.isEmpty() ? "." : shape(cell.occupiedBy()));
                if (cell.item() != null) out.append('(').append(cell.item().symbol()).append(')');
                out.append(' ');
            }
            out.append('|').append('\n');
        }
        sink.append(out.toString());
    }

    private static String piece(Piece piece) {
        if (piece == null) return "none";
        var items = new TreeMap<Integer, String>();
        piece.items().forEach((index, item) -> items.put(index, item.getClass().getSimpleName() + "(" + item.symbol() + ")"));
        return shape(piece.shape()) + " items=" + items;
    }

    private static String shape(Shape shape) {
        if (shape == TemporaryBlock.INSTANCE) return "#";
        if (shape == SoloBlock.SHAPE) return "B";
        return shape.toString();
    }
}
