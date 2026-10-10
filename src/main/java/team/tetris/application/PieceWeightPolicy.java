package team.tetris.application;

import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import team.tetris.application.model.Difficulty;
import team.tetris.core.TetrominoType;

/**
 * 난이도별 블록 등장 가중치. 다른 블록의 가중치가 10일 때 I 블록은 easy 12(20% 더 자주),
 * normal 10, hard 8(20% 덜)이다. 실제 확률은 I가 약 16.7% / 14.3% / 11.8%다.
 */
public final class PieceWeightPolicy {

    public static final int BASE_WEIGHT = 10;

    private PieceWeightPolicy() {
    }

    public static Map<TetrominoType, Integer> weightsFor(Difficulty difficulty) {
        int iWeight = switch (Objects.requireNonNull(difficulty, "difficulty")) {
            case EASY -> 12;
            case NORMAL -> BASE_WEIGHT;
            case HARD -> 8;
        };
        Map<TetrominoType, Integer> weights = new EnumMap<>(TetrominoType.class);
        for (TetrominoType type : TetrominoType.values()) {
            weights.put(type, type == TetrominoType.I ? iWeight : BASE_WEIGHT);
        }
        return Map.copyOf(weights);
    }
}
