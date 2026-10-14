package team.tetris.application;

import static org.junit.jupiter.api.Assertions.*;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import team.tetris.application.debug.DebugSetup;
import team.tetris.application.model.Difficulty;
import team.tetris.core.Cell;

class DebugSetupTest {
    @ParameterizedTest @EnumSource(DebugSetup.HolePattern.class)
    void rowsHaveTheRequestedHoleCountAndTheSpawnAreaStaysEmpty(DebugSetup.HolePattern pattern) {
        for (int rows : new int[]{0, 1, 18}) {
            for (int holes : new int[]{0, 1, 5, 10}) {
                var setup = new DebugSetup(rows, holes, pattern, 0, Difficulty.NORMAL, 13);
                Cell[][] board = setup.board();
                for (int y = 0; y < 20; y++) {
                    assertEquals(y < 20 - rows ? 10 : holes, Arrays.stream(board[y]).filter(Cell::isEmpty).count());
                }
                assertArrayEquals(board, setup.board());
            }
        }
    }

    @Test void alignedHolesShareColumnsWhileRandomRowsAndSeedsVary() {
        var aligned = new DebugSetup(10, 3, DebugSetup.HolePattern.ALIGNED, 0, Difficulty.NORMAL, 13).board();
        for (int row = 11; row < 20; row++) assertArrayEquals(aligned[10], aligned[row]);
        var first = new DebugSetup(10, 3, DebugSetup.HolePattern.RANDOM, 0, Difficulty.NORMAL, 13).board();
        var second = new DebugSetup(10, 3, DebugSetup.HolePattern.RANDOM, 0, Difficulty.NORMAL, 14).board();
        assertFalse(Arrays.deepEquals(first, second));
        assertFalse(Arrays.equals(first[10], first[11]));
    }

    @Test void invalidBoardAndStatsAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> new DebugSetup(19, 1, DebugSetup.HolePattern.ALIGNED, 0, Difficulty.NORMAL, 13));
        assertThrows(IllegalArgumentException.class, () -> new DebugSetup(1, 11, DebugSetup.HolePattern.ALIGNED, 0, Difficulty.NORMAL, 13));
        assertThrows(IllegalArgumentException.class, () -> new DebugSetup(1, 1, DebugSetup.HolePattern.ALIGNED, -1, Difficulty.NORMAL, 13));
    }
}
