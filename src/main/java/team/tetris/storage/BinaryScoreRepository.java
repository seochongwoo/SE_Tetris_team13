package team.tetris.storage;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.UUID;
import team.tetris.application.model.ScoreEntry;
import team.tetris.application.model.ScoreStore;
import team.tetris.application.port.ScoreRepository;
import team.tetris.application.port.StorageException;

/** Java 데이터 스트림 기반 기록 저장소. 순위와 등록 이력의 일괄 저장. */
public final class BinaryScoreRepository extends AtomicFileRepository<ScoreStore> implements ScoreRepository {
    private static final int MAGIC = 0x54545343;

    public BinaryScoreRepository(Path path) { super(path); }
    BinaryScoreRepository(Path path, Commit commit) { super(path, commit); }

    @Override
    public ScoreStore load() throws StorageException { return read().orElseGet(ScoreStore::empty); }
    @Override
    public void save(ScoreStore store) throws StorageException { write(store); }

    @Override
    ScoreStore decode(byte[] contents) throws IOException {
        try (var input = new DataInputStream(new ByteArrayInputStream(contents))) {
            if (input.readInt() != MAGIC) throw new IOException("Invalid score file signature");
            requireVersion(input.readInt());
            int size = count(input);
            var entries = new ArrayList<ScoreEntry>();
            for (int i = 0; i < size; i++) {
                UUID recordId = uuid(input);
                UUID gameId = uuid(input);
                String name = input.readUTF();
                long score = input.readLong();
                long seconds = input.readLong();
                int nanos = input.readInt();
                if (nanos < 0 || nanos > 999_999_999) throw new IOException("Invalid timestamp");
                entries.add(new ScoreEntry(recordId, gameId, name, score, Instant.ofEpochSecond(seconds, nanos)));
            }
            int receipts = count(input);
            var registrations = new HashMap<UUID, UUID>();
            for (int i = 0; i < receipts; i++) {
                if (registrations.put(uuid(input), uuid(input)) != null) throw new IOException("Duplicate game ID");
            }
            if (input.read() != -1) throw new IOException("Trailing score data");
            return new ScoreStore(entries, registrations);
        }
    }

    private static int count(DataInputStream input) throws IOException {
        int count = input.readInt();
        if (count < 0 || count > input.available()) throw new IOException("Invalid record count");
        return count;
    }

    private static UUID uuid(DataInputStream input) throws IOException {
        return new UUID(input.readLong(), input.readLong());
    }

    private static void uuid(DataOutputStream output, UUID id) throws IOException {
        output.writeLong(id.getMostSignificantBits());
        output.writeLong(id.getLeastSignificantBits());
    }

    @Override
    byte[] encode(ScoreStore store) throws IOException {
        var bytes = new ByteArrayOutputStream();
        try (var output = new DataOutputStream(bytes)) {
            output.writeInt(MAGIC);
            output.writeInt(1);
            output.writeInt(store.entries().size());
            for (ScoreEntry entry : store.entries()) {
                uuid(output, entry.recordId());
                uuid(output, entry.gameId());
                output.writeUTF(entry.name());
                output.writeLong(entry.score());
                output.writeLong(entry.registeredAt().getEpochSecond());
                output.writeInt(entry.registeredAt().getNano());
            }
            output.writeInt(store.registrations().size());
            for (var registration : store.registrations().entrySet()) {
                uuid(output, registration.getKey());
                uuid(output, registration.getValue());
            }
        }
        return bytes.toByteArray();
    }
}
