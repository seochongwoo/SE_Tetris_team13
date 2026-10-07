package team.tetris.application;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import team.tetris.application.model.Difficulty;

class SpeedPolicyTest {
    private final SpeedPolicy policy = new SpeedPolicy();

    @ParameterizedTest
    @CsvSource({"0,0,1000", "9,0,1000", "10,1,900", "89,8,200",
            "90,9,100", "100,10,100", "2147483647,214748364,100"})
    void derivesLevelAndClampsInterval(int lines, int level, long milliseconds) {
        assertEquals(level, policy.levelFor(lines));
        assertEquals(milliseconds * 1_000_000L, policy.gravityIntervalNanos(level));
    }

    @ParameterizedTest
    @CsvSource({"EASY,1,920", "NORMAL,1,900", "HARD,1,880",
            "EASY,5,600", "NORMAL,5,500", "HARD,5,400",
            "EASY,11,120", "EASY,12,100", "NORMAL,9,100", "HARD,8,100"})
    void changesAccelerationByTwentyPercent(Difficulty difficulty, int level, long milliseconds) {
        assertEquals(milliseconds * 1_000_000L, new SpeedPolicy(difficulty).gravityIntervalNanos(level));
    }

    @ParameterizedTest
    @EnumSource(Difficulty.class)
    void allDifficultiesShareInitialIntervalAndLevelBoundariesAndRemainBounded(Difficulty difficulty) {
        var speed = new SpeedPolicy(difficulty);
        assertEquals(1_000_000_000L, speed.gravityIntervalNanos(0));
        assertEquals(0, speed.levelFor(9));
        assertEquals(1, speed.levelFor(10));
        assertEquals(100_000_000L, speed.gravityIntervalNanos(Integer.MAX_VALUE));
        assertThrows(IllegalArgumentException.class, () -> speed.gravityIntervalNanos(-1));
        assertThrows(IllegalArgumentException.class, () -> speed.levelFor(-1));
    }

    @Test
    void handlesLargestLevelAndRejectsNegativeInputs() {
        assertThrows(NullPointerException.class, () -> new SpeedPolicy(null));
        assertEquals(100_000_000L, policy.gravityIntervalNanos(Integer.MAX_VALUE));
        assertThrows(IllegalArgumentException.class, () -> policy.levelFor(-1));
        assertThrows(IllegalArgumentException.class, () -> policy.gravityIntervalNanos(-1));
    }
}
