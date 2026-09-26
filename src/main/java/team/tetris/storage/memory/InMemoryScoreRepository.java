package team.tetris.storage.memory;

import java.util.Objects;
import team.tetris.application.model.ScoreStore;
import team.tetris.application.port.ScoreRepository;

/** 기록 서비스 검증용 메모리 저장소. */
public final class InMemoryScoreRepository implements ScoreRepository {
    private ScoreStore value = ScoreStore.empty();
    public ScoreStore load() { return value; }
    public void save(ScoreStore store) { value = Objects.requireNonNull(store, "store"); }
}
