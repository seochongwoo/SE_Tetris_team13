package team.tetris.network;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import team.tetris.application.model.Difficulty;
import team.tetris.application.network.MatchEnvelope;
import team.tetris.application.network.MatchMessage;
import team.tetris.application.network.MatchMessage.*;
import team.tetris.application.network.PlayerState;
import team.tetris.application.network.PlayerState.*;

/** 길이 제한을 둔 명시적 binary protocol. enum ordinal이나 ObjectInputStream을 사용하지 않는다. */
public final class MessageCodec {
    public static final int MAX_FRAME_BYTES = 16_384;
    private static final int MAGIC = 0x54455433; // TET3

    public void write(DataOutputStream output, MatchEnvelope envelope) throws IOException {
        var buffer = new ByteArrayOutputStream();
        var out = new DataOutputStream(buffer);
        out.writeInt(MAGIC);
        out.writeLong(envelope.matchId());
        out.writeLong(envelope.sequence());
        switch (envelope.message()) {
            case Hello m -> { out.writeUTF("HELLO"); out.writeInt(m.version()); out.writeUTF(m.role().name()); }
            case Lobby m -> {
                out.writeUTF("LOBBY"); out.writeLong(m.revision()); out.writeUTF(m.mode().name());
                out.writeUTF(m.difficulty().name()); out.writeInt(m.timeLimitSeconds());
            }
            case Ready m -> { out.writeUTF("READY"); out.writeLong(m.revision()); out.writeBoolean(m.ready()); }
            case Start m -> { out.writeUTF("START"); out.writeLong(m.revision()); out.writeLong(m.pieceSeed()); }
            case State m -> { out.writeUTF("STATE"); writePlayer(out, m.player()); out.writeLong(m.remainingNanos()); }
            case Attack m -> { out.writeUTF("ATTACK"); out.writeLong(m.eventId()); writeRows(out, m.rows()); }
            case Pause m -> { out.writeUTF("PAUSE"); out.writeUTF(m.owner().name()); out.writeBoolean(m.request()); }
            case Resume m -> { out.writeUTF("RESUME"); out.writeBoolean(m.request()); }
            case Result m -> { out.writeUTF("RESULT"); out.writeUTF(m.outcome().name()); out.writeUTF(m.reason().name()); }
            case Leave ignored -> out.writeUTF("LEAVE");
            case Ping m -> { out.writeUTF("PING"); out.writeLong(m.id()); }
            case Pong m -> { out.writeUTF("PONG"); out.writeLong(m.id()); }
        }
        if (buffer.size() > MAX_FRAME_BYTES) throw new IOException("Frame too large");
        output.writeInt(buffer.size());
        buffer.writeTo(output);
        output.flush();
    }

    public MatchEnvelope read(DataInputStream input) throws IOException {
        int size = input.readInt();
        if (size <= 0 || size > MAX_FRAME_BYTES) throw new IOException("Invalid frame length: " + size);
        byte[] bytes = new byte[size];
        input.readFully(bytes);
        var in = new DataInputStream(new ByteArrayInputStream(bytes));
        try {
            if (in.readInt() != MAGIC) throw new IOException("Invalid protocol magic");
            long match = in.readLong();
            long sequence = in.readLong();
            MatchMessage message = switch (in.readUTF()) {
                case "HELLO" -> new Hello(in.readInt(), value(in, Role.class));
                case "LOBBY" -> new Lobby(in.readLong(), value(in, Mode.class), value(in, Difficulty.class), in.readInt());
                case "READY" -> new Ready(in.readLong(), bool(in));
                case "START" -> new Start(in.readLong(), in.readLong());
                case "STATE" -> new State(readPlayer(in), in.readLong());
                case "ATTACK" -> new Attack(in.readLong(), readRows(in));
                case "PAUSE" -> new Pause(value(in, Role.class), bool(in));
                case "RESUME" -> new Resume(bool(in));
                case "RESULT" -> new Result(value(in, Outcome.class), value(in, EndReason.class));
                case "LEAVE" -> new Leave();
                case "PING" -> new Ping(in.readLong());
                case "PONG" -> new Pong(in.readLong());
                default -> throw new IOException("Unknown message type");
            };
            if (in.available() != 0) throw new IOException("Trailing frame data");
            return new MatchEnvelope(match, sequence, message);
        } catch (IllegalArgumentException e) {
            throw new IOException("Invalid message fields", e);
        }
    }

    private static boolean bool(DataInputStream in) throws IOException {
        int value = in.readUnsignedByte();
        if (value > 1) throw new IOException("Invalid boolean");
        return value == 1;
    }

    private static <E extends Enum<E>> E value(DataInputStream in, Class<E> type) throws IOException {
        return Enum.valueOf(type, in.readUTF());
    }

    private static void writeTile(DataOutputStream out, Tile tile) throws IOException {
        out.writeUTF(tile.shape().name()); out.writeUTF(tile.item().name());
    }
    private static Tile readTile(DataInputStream in) throws IOException {
        return new Tile(value(in, ShapeCode.class), value(in, ItemCode.class));
    }
    private static void writePieces(DataOutputStream out, List<PlacedTile> tiles) throws IOException {
        out.writeInt(tiles.size());
        for (PlacedTile tile : tiles) {
            out.writeInt(tile.x()); out.writeInt(tile.y()); writeTile(out, tile.tile());
        }
    }
    private static List<PlacedTile> readPieces(DataInputStream in) throws IOException {
        int count = count(in, PlayerState.MAX_PIECE_CELLS);
        List<PlacedTile> tiles = new ArrayList<>();
        for (int i = 0; i < count; i++) tiles.add(new PlacedTile(in.readInt(), in.readInt(), readTile(in)));
        return tiles;
    }
    private static void writeRows(DataOutputStream out, List<Integer> rows) throws IOException {
        out.writeInt(rows.size());
        for (int row : rows) out.writeInt(row);
    }
    private static List<Integer> readRows(DataInputStream in) throws IOException {
        int count = count(in, 10);
        List<Integer> rows = new ArrayList<>();
        for (int i = 0; i < count; i++) rows.add(in.readInt());
        return rows;
    }
    private static int count(DataInputStream in, int max) throws IOException {
        int count = in.readInt();
        if (count < 0 || count > max) throw new IOException("Invalid collection size");
        return count;
    }
    private static void writePlayer(DataOutputStream out, PlayerState player) throws IOException {
        for (Tile tile : player.board()) writeTile(out, tile);
        writePieces(out, player.active()); writePieces(out, player.next());
        out.writeLong(player.score()); out.writeInt(player.level()); out.writeInt(player.clearedLines());
        out.writeInt(player.doubleScoreClears()); out.writeBoolean(player.defeated());
        writeRows(out, player.pendingRows());
    }
    private static PlayerState readPlayer(DataInputStream in) throws IOException {
        List<Tile> board = new ArrayList<>();
        for (int i = 0; i < PlayerState.WIDTH * PlayerState.HEIGHT; i++) board.add(readTile(in));
        return new PlayerState(board, readPieces(in), readPieces(in), in.readLong(), in.readInt(),
                in.readInt(), in.readInt(), bool(in), readRows(in));
    }
}
