package team.tetris.application.network;

import java.util.Arrays;
import java.util.List;
import java.util.Random;
import team.tetris.application.*;
import team.tetris.application.model.Difficulty;
import team.tetris.application.network.MatchMessage.*;
import team.tetris.core.*;
import team.tetris.core.item.ItemCatalog;
import team.tetris.core.rule.*;

public final class P2pMatchTestSupport {
    private P2pMatchTestSupport() {}
    public static Cell[][] empty() {
        var board = new Cell[20][10];
        for (var row : board) Arrays.fill(row, Cell.EMPTY);
        return board;
    }
    public static Cell[][] twoLines() {
        var board = empty();
        for (int y = 18; y < 20; y++) for (int x = 0; x < 10; x++)
            if (x != 4 && x != 5) board[y][x] = Cell.occupiedBy(TetrominoType.T);
        return board;
    }
    public static P2pMatch match(P2pLobby.StartedMatch started, Cell[][] board) {
        return new P2pMatch(started, incoming -> {
            PieceSource source = new PlainPieceSource(new WeightedRandomGenerator(
                    PieceWeightPolicy.weightsFor(started.configuration().difficulty()), new Random(started.pieceSeed())));
            if (started.configuration().mode() == Mode.ITEM)
                source = new ItemPieceSource(source, ItemCatalog.all(), new Random(37 + started.connection().role().ordinal()));
            return board == null ? new PlayerEngine(10, 20, source, incoming)
                    : new PlayerEngine(board, new ActivePiece(TetrominoType.O, 0, new Position(3, 0)), source, incoming);
        }, new ScorePolicy(), new SpeedPolicy());
    }
    public static final class Pair implements AutoCloseable {
        public final LobbyTestSupport.Transport st = new LobbyTestSupport.Transport(), ct = new LobbyTestSupport.Transport();
        public final P2pLobby server = lobby(st), client = lobby(ct);
        public P2pMatch s, c;
        public P2pLobby.StartedMatch ss, cs;
        public Pair(Mode mode, Cell[][] sb, Cell[][] cb) {
            st.peer = ct; ct.peer = st;
            server.host(); client.connect("127.0.0.1"); st.connected(); ct.connected();
            server.update(); client.update();
            server.configure(mode, mode == Mode.TIMED ? 60 : 0); client.update();
            start(sb, cb);
        }
        private P2pLobby lobby(LobbyTestSupport.Transport transport) {
            return new P2pLobby(() -> transport, List::of, new RecentIps(new LobbyTestSupport.MemoryIps()), Difficulty.NORMAL, () -> 42);
        }
        public void start(Cell[][] sb, Cell[][] cb) {
            server.ready(); client.ready(); server.update(); client.update();
            ss = server.takeStartedMatch().orElseThrow(); cs = client.takeStartedMatch().orElseThrow();
            s = match(ss, sb); c = match(cs, cb); pump();
        }
        public void pump() { s.update(0); c.update(0); s.update(0); }
        public void rematch() {
            server.returnAfterResult(); client.returnAfterResult(); client.update();
            start(null, null);
        }
        public void close() { server.close(); client.close(); }
    }
}
