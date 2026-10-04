package team.tetris.application.model;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** 순위 목록과 중복 등록 방지 이력의 일괄 저장. */
public record ScoreStore(List<ScoreEntry> entries, Map<UUID, UUID> registrations) {
    public ScoreStore {
        entries = List.copyOf(entries);
        registrations = Map.copyOf(registrations);
        var games = new HashSet<UUID>();
        if (new HashSet<>(registrations.values()).size() != registrations.size()) {
            throw new IllegalArgumentException("Record IDs must be unique");
        }
        for (ScoreEntry entry : entries) {
            if (!games.add(entry.gameId()) || !entry.recordId().equals(registrations.get(entry.gameId()))) {
                throw new IllegalArgumentException("Duplicate game or missing registration receipt");
            }
        }
    }

    public static ScoreStore empty() { return new ScoreStore(List.of(), Map.of()); }
}
