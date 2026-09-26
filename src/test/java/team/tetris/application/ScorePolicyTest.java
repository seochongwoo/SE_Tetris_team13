package team.tetris.application;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class ScorePolicyTest {
    private final ScorePolicy policy = new ScorePolicy();

    @ParameterizedTest
    @CsvSource({"0,0,0,0", "1,0,0,1", "20,0,0,20", "3,1,0,103",
            "3,1,1,106", "0,2,0,400", "0,3,0,900", "0,4,0,1600",
            "20,0,2147483647,42949672960"})
    void scoresDistanceAndLinesUsingPreviousLevel(int distance, int lines, int level, long expected) {
        assertEquals(expected, policy.scoreFor(distance, lines, level));
    }

    @Test
    void rejectsNegativeInputsAndUnrepresentableScore() {
        assertThrows(IllegalArgumentException.class, () -> policy.scoreFor(-1, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> policy.scoreFor(0, -1, 0));
        assertThrows(IllegalArgumentException.class, () -> policy.scoreFor(0, 0, -1));
        assertThrows(ArithmeticException.class, () -> policy.scoreFor(0, Integer.MAX_VALUE, 0));
    }
}
