package team.tetris.application;

import java.util.Objects;

/** 모드·난이도 조합별 기록 보관 개수와 이름 길이의 교체 가능한 기준안. */
public record ScoreboardPolicy(int capacity, int maxNameCodePoints, boolean allowAborted) {
    public ScoreboardPolicy {
        if (capacity < 10 || maxNameCodePoints < 1) {
            throw new IllegalArgumentException("At least ten records and a positive name limit required");
        }
    }

    public static ScoreboardPolicy defaults() {
        return new ScoreboardPolicy(10, 12, false);
    }

    public String normalizeName(String name) {
        Objects.requireNonNull(name, "name");
        if (name.codePoints().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("Control characters are not allowed");
        }
        String normalized = name.strip();
        if (normalized.isBlank() || normalized.codePointCount(0, normalized.length()) > maxNameCodePoints) {
            throw new IllegalArgumentException("Name length is outside the allowed range");
        }
        return normalized;
    }

    public boolean accepts(GameResult result) {
        return allowAborted || result.reason() == GameStatus.GAME_OVER;
    }
}
