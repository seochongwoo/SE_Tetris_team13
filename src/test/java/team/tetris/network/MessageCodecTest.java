package team.tetris.network;

import static org.junit.jupiter.api.Assertions.*;
import static team.tetris.application.network.MatchMessage.*;
import static team.tetris.application.network.PlayerState.*;

import java.io.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;
import team.tetris.application.model.Difficulty;
import team.tetris.application.network.*;

class MessageCodecTest {
    private final MessageCodec codec = new MessageCodec();

    static PlayerState player() {
        var board = new ArrayList<>(Collections.nCopies(200, new Tile(ShapeCode.EMPTY, ItemCode.NONE)));
        board.set(190, new Tile(ShapeCode.ATTACK, ItemCode.NONE));
        board.set(199, new Tile(ShapeCode.T, ItemCode.LINE_CLEAR));
        var weight = List.of(new PlacedTile(1, 0, new Tile(ShapeCode.WEIGHT, ItemCode.WEIGHT)),
                new PlacedTile(2, 0, new Tile(ShapeCode.WEIGHT, ItemCode.NONE)),
                new PlacedTile(0, 1, new Tile(ShapeCode.WEIGHT, ItemCode.NONE)),
                new PlacedTile(1, 1, new Tile(ShapeCode.WEIGHT, ItemCode.NONE)),
                new PlacedTile(2, 1, new Tile(ShapeCode.WEIGHT, ItemCode.NONE)),
                new PlacedTile(3, 1, new Tile(ShapeCode.WEIGHT, ItemCode.NONE)));
        return new PlayerState(board, weight, weight, 12345, 3, 22, 2, false, List.of(1022, 1007));
    }

    private byte[] encode(MatchEnvelope envelope) throws IOException {
        var bytes = new ByteArrayOutputStream();
        codec.write(new DataOutputStream(bytes), envelope);
        return bytes.toByteArray();
    }

    private MatchEnvelope decode(byte[] bytes) throws IOException {
        return codec.read(new DataInputStream(new ByteArrayInputStream(bytes)));
    }

    @Test
    void allMessageTypesRoundTripIncludingSixCellWeightAndItemAttributes() throws Exception {
        List<MatchMessage> messages = List.of(new Hello(1, Role.SERVER),
                new Lobby(1, Mode.TIMED, Difficulty.HARD, 180), new Ready(1, true), new Start(1, Long.MIN_VALUE),
                new State(player(), 100, 12), new Painted(12), new Attack(3, List.of(1, 5)), new Pause(Role.CLIENT, true),
                new Resume(false), new Result(Outcome.DRAW, EndReason.TIME_LIMIT),
                new Result(Outcome.SERVER_WINS, EndReason.TOP_OUT, player(), player()), new Leave(), new Ping(9), new Pong(9));
        for (MatchMessage message : messages) {
            var envelope = new MatchEnvelope(12, 37, message);
            assertEquals(envelope, decode(encode(envelope)));
        }
    }

    @Test
    void fragmentedReadsAndConsecutiveFramesAreDecodedExactly() throws Exception {
        var first = new MatchEnvelope(0, 1, new Ready(2, true));
        var second = new MatchEnvelope(3, 2, new Attack(1, List.of(5, 9)));
        var bytes = new ByteArrayOutputStream();
        bytes.write(encode(first)); bytes.write(encode(second));
        InputStream fragmented = new FilterInputStream(new ByteArrayInputStream(bytes.toByteArray())) {
            @Override public int read(byte[] b, int off, int len) throws IOException {
                return super.read(b, off, Math.min(1, len));
            }
        };
        var input = new DataInputStream(fragmented);
        assertEquals(first, codec.read(input));
        assertEquals(second, codec.read(input));
        assertThrows(EOFException.class, () -> codec.read(input));
    }

    @Test
    void rejectsTruncationOversizedNegativeLengthsBadMagicAndTrailingBytes() throws Exception {
        byte[] valid = encode(new MatchEnvelope(0, 1, new Ping(1)));
        for (int length = 0; length < valid.length; length++) {
            byte[] truncated = Arrays.copyOf(valid, length);
            assertThrows(IOException.class, () -> decode(truncated));
        }
        for (int length : new int[]{-1, 0, Integer.MAX_VALUE, MessageCodec.MAX_FRAME_BYTES + 1}) {
            var bytes = new ByteArrayOutputStream();
            new DataOutputStream(bytes).writeInt(length);
            assertThrows(IOException.class, () -> decode(bytes.toByteArray()));
        }
        byte[] badMagic = valid.clone(); badMagic[4] = 0;
        assertThrows(IOException.class, () -> decode(badMagic));
        byte[] trailing = Arrays.copyOf(valid, valid.length + 1);
        trailing[3]++;
        assertThrows(IOException.class, () -> decode(trailing));
    }

    @Test
    void rejectsUnknownTypeEnumInvalidBooleanAndUnboundedRowCount() throws Exception {
        assertThrows(IOException.class, () -> decode(frame("BOGUS", out -> {})));
        assertThrows(IOException.class, () -> decode(frame("HELLO", out -> {out.writeInt(1); out.writeUTF("BOGUS");})));
        assertThrows(IOException.class, () -> decode(frame("READY", out -> {out.writeLong(1); out.writeByte(2);})));
        assertThrows(IOException.class, () -> decode(frame("ATTACK", out -> {out.writeLong(1); out.writeInt(Integer.MAX_VALUE);})));
        assertThrows(IOException.class, () -> decode(frame("ATTACK", out -> {
            out.writeLong(1); out.writeInt(2); out.writeInt(1023); out.writeInt(1);
        })));
    }

    @FunctionalInterface private interface Payload { void write(DataOutputStream out) throws IOException; }
    private byte[] frame(String type, Payload payload) throws IOException {
        var body = new ByteArrayOutputStream(); var out = new DataOutputStream(body);
        out.writeInt(0x54455433); out.writeLong(0); out.writeLong(1); out.writeUTF(type); payload.write(out);
        var frame = new ByteArrayOutputStream(); new DataOutputStream(frame).writeInt(body.size()); body.writeTo(frame);
        return frame.toByteArray();
    }

    @Test
    void valuesAreImmutableAndRejectImpossibleAttacksAndSettings() {
        var source = new ArrayList<>(List.of(7, 9));
        var attack = new Attack(1, source); source.clear();
        assertEquals(List.of(7, 9), attack.rows());
        assertThrows(UnsupportedOperationException.class, () -> attack.rows().clear());
        assertThrows(IllegalArgumentException.class, () -> new Attack(0, List.of(7, 9)));
        assertThrows(IllegalArgumentException.class, () -> new Attack(1, List.of(7)));
        assertThrows(IllegalArgumentException.class, () -> new Attack(1, Collections.nCopies(11, 7)));
        assertThrows(IllegalArgumentException.class, () -> new Lobby(1, Mode.TIMED, Difficulty.NORMAL, 10));
        assertThrows(IllegalArgumentException.class, () -> new Lobby(1, Mode.NORMAL, Difficulty.NORMAL, 60));
        assertThrows(IllegalArgumentException.class, () -> new Tile(ShapeCode.EMPTY, ItemCode.BOMB));
        assertThrows(IllegalArgumentException.class, () -> new MatchEnvelope(-1, 1, new Leave()));
        assertThrows(IllegalArgumentException.class, () -> new Result(Outcome.DRAW, EndReason.TOP_OUT, player(), null));
        assertThrows(IllegalArgumentException.class, () -> new Result(Outcome.DRAW, EndReason.TOP_OUT, null, player()));
    }
}
