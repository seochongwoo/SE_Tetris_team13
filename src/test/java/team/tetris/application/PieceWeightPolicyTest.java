package team.tetris.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Map;
import org.junit.jupiter.api.Test;
import team.tetris.application.model.Difficulty;
import team.tetris.core.TetrominoType;

class PieceWeightPolicyTest {

    @Test
    void iPieceIsTwentyPercentMoreOnEasyAndTwentyPercentLessOnHard() {
        assertEquals(12, PieceWeightPolicy.weightsFor(Difficulty.EASY).get(TetrominoType.I));
        assertEquals(10, PieceWeightPolicy.weightsFor(Difficulty.NORMAL).get(TetrominoType.I));
        assertEquals(8, PieceWeightPolicy.weightsFor(Difficulty.HARD).get(TetrominoType.I));
    }

    @Test
    void everyOtherPieceKeepsTheBaseWeightOnEveryDifficulty() {
        for (Difficulty difficulty : Difficulty.values()) {
            Map<TetrominoType, Integer> weights = PieceWeightPolicy.weightsFor(difficulty);
            assertEquals(TetrominoType.values().length, weights.size());
            for (TetrominoType type : TetrominoType.values()) {
                if (type != TetrominoType.I) {
                    assertEquals(PieceWeightPolicy.BASE_WEIGHT, weights.get(type), difficulty + " " + type);
                }
            }
        }
    }

    @Test
    void difficultyIsRequired() {
        assertThrows(NullPointerException.class, () -> PieceWeightPolicy.weightsFor(null));
    }
}
