package team.tetris.application;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class SpeedPolicyTest {
    private final SpeedPolicy policy = new SpeedPolicy();

    @ParameterizedTest
    @CsvSource({"0,0,1000", "9,0,1000", "10,1,900", "89,8,200",
            "90,9,100", "100,10,100", "2147483647,214748364,100"})
    void derivesLevelAndClampsInterval(int lines, int level, long milliseconds) {
        assertEquals(level, policy.levelFor(lines));
        assertEquals(milliseconds * 1_000_000L, policy.gravityIntervalNanos(level));
    }

    @Test
    void handlesLargestLevelAndRejectsNegativeInputs() {
        assertEquals(100_000_000L, policy.gravityIntervalNanos(Integer.MAX_VALUE));
        assertThrows(IllegalArgumentException.class, () -> policy.levelFor(-1));
        assertThrows(IllegalArgumentException.class, () -> policy.gravityIntervalNanos(-1));
    }
}
