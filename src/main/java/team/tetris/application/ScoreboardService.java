package team.tetris.application;

import java.time.Clock;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import team.tetris.application.model.LoadResult;
import team.tetris.application.model.GameMode;
import team.tetris.application.model.Difficulty;
import team.tetris.application.model.ScoreEntry;
import team.tetris.application.model.ScoreStore;
import team.tetris.application.port.ScoreRepository;
import team.tetris.application.port.StorageException;

/** 단일 실행 흐름의 순위 관리. 동점은 저장 순서 유지. */
public final class ScoreboardService {
    private final ScoreRepository repository;
    private final ScoreboardPolicy policy;
    private final Clock clock;

    public ScoreboardService(ScoreRepository repository) {
        this(repository, ScoreboardPolicy.defaults(), Clock.systemUTC());
    }

    public ScoreboardService(ScoreRepository repository, ScoreboardPolicy policy, Clock clock) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.policy = Objects.requireNonNull(policy, "policy");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public List<ScoreEntry> list() throws StorageException { return ranked(repository.load().entries()); }

    /** 모드·난이도 조합별 순위. 다른 조합의 기록은 정원에 영향을 주지 않는다. */
    public List<ScoreEntry> list(GameMode mode, Difficulty difficulty) throws StorageException {
        Objects.requireNonNull(mode, "mode");
        Objects.requireNonNull(difficulty, "difficulty");
        return category(list(), mode, difficulty);
    }

    public LoadResult<List<ScoreEntry>> loadOrEmpty(GameMode mode, Difficulty difficulty) {
        try { return new LoadResult<>(list(mode, difficulty), Optional.empty()); }
        catch (StorageException error) { return new LoadResult<>(List.of(), Optional.of(error)); }
    }

    /** 재시작한 종료 조율자에서 기존 등록 결과 조회. */
    public Optional<UUID> registeredRecordId(UUID gameId) throws StorageException {
        return Optional.ofNullable(repository.load().registrations().get(Objects.requireNonNull(gameId, "gameId")));
    }

    public LoadResult<List<ScoreEntry>> loadOrEmpty() {
        try { return new LoadResult<>(list(), Optional.empty()); }
        catch (StorageException error) { return new LoadResult<>(List.of(), Optional.of(error)); }
    }

    public boolean qualifies(GameResult result) throws StorageException {
        Objects.requireNonNull(result, "result");
        ScoreStore store = repository.load();
        return !store.registrations().containsKey(result.gameId()) && policy.accepts(result)
                && fits(store, result);
    }

    /** 등록 시점 순위 재판단. 저장 성공 후 기록 ID 반환, 중복 요청은 최초 ID 반환. */
    public Optional<UUID> register(GameResult result, String name) throws StorageException {
        Objects.requireNonNull(result, "result");
        ScoreStore store = repository.load();
        UUID existing = store.registrations().get(result.gameId());
        if (existing != null) return Optional.of(existing);
        String validated = policy.normalizeName(name);
        if (!policy.accepts(result) || !fits(store, result)) return Optional.empty();
        ScoreEntry entry = new ScoreEntry(UUID.randomUUID(), result.gameId(), validated, result.score(), clock.instant(),
                result.mode(), result.difficulty());
        var entries = new ArrayList<>(store.entries());
        entries.add(entry);
        var receipts = new HashMap<>(store.registrations());
        receipts.put(entry.gameId(), entry.recordId());
        repository.save(new ScoreStore(ranked(entries), receipts));
        return Optional.of(entry.recordId());
    }

    /** 목록과 등록 이력의 명시적 전체 삭제. 설정과 독립 처리. */
    public void clear() throws StorageException { repository.save(ScoreStore.empty()); }

    private record Category(GameMode mode, Difficulty difficulty) { }

    private List<ScoreEntry> ranked(List<ScoreEntry> entries) {
        var counts = new HashMap<Category, Integer>();
        var retained = new ArrayList<ScoreEntry>();
        for (ScoreEntry entry : entries.stream()
                .sorted(Comparator.comparingLong(ScoreEntry::score).reversed()).toList()) {
            var category = new Category(entry.mode(), entry.difficulty());
            if (counts.merge(category, 1, Integer::sum) <= policy.capacity()) retained.add(entry);
        }
        return List.copyOf(retained);
    }

    private static List<ScoreEntry> category(List<ScoreEntry> entries, GameMode mode, Difficulty difficulty) {
        return entries.stream().filter(entry -> entry.mode() == mode && entry.difficulty() == difficulty).toList();
    }

    private boolean fits(ScoreStore store, GameResult result) {
        List<ScoreEntry> ranked = category(ranked(store.entries()), result.mode(), result.difficulty());
        return ranked.size() < policy.capacity() || result.score() > ranked.getLast().score();
    }
}
