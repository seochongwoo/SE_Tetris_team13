package team.tetris.application.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** 저장된 기록과 게임 식별자, 등록 시각 보관. 동점 순서는 목록 순서로 판단. */
public record ScoreEntry(UUID recordId, UUID gameId, String name, long score, Instant registeredAt,
                         GameMode mode, Difficulty difficulty) {
    public ScoreEntry(UUID recordId, UUID gameId, String name, long score, Instant registeredAt) {
        this(recordId, gameId, name, score, registeredAt, GameMode.NORMAL, Difficulty.NORMAL);
    }

    public ScoreEntry {
        Objects.requireNonNull(recordId, "recordId");
        Objects.requireNonNull(gameId, "gameId");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(registeredAt, "registeredAt");
        Objects.requireNonNull(mode, "mode");
        Objects.requireNonNull(difficulty, "difficulty");
        if (score < 0 || name.isBlank() || !name.equals(name.strip())
                || name.codePoints().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("Invalid score record");
        }
    }
}
