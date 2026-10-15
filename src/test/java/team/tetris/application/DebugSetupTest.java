package team.tetris.application;

import static org.junit.jupiter.api.Assertions.*;
import java.util.Arrays;
import java.util.HashSet;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import team.tetris.application.debug.DebugSetup;
import team.tetris.application.model.Difficulty;
import team.tetris.core.Cell;
import team.tetris.core.Shape;
import team.tetris.core.TemporaryBlock;
import team.tetris.core.TetrominoType;

class DebugSetupTest {
    @ParameterizedTest @EnumSource(DebugSetup.HolePattern.class)
    void rowsHaveTheRequestedHoleCountAndTheSpawnAreaStaysEmpty(DebugSetup.HolePattern pattern) {
        for (int rows : new int[]{0, 1, 18}) {
            for (int holes : new int[]{0, 1, 5, 10}) {
                for (var fillMode : DebugSetup.FillMode.values()) {
                    var setup = new DebugSetup(rows, holes, pattern, fillMode, 0, Difficulty.NORMAL, 13);
                    Cell[][] board = setup.board();
                    for (int y = 0; y < 20; y++) {
                        assertEquals(y < 20 - rows ? 10 : holes, Arrays.stream(board[y]).filter(Cell::isEmpty).count());
                    }
                    assertArrayEquals(board, setup.board());
                }
            }
        }
    }

    @ParameterizedTest @EnumSource(DebugSetup.HolePattern.class)
    void fillModesUseTemporaryOrRandomBlocksWithoutMovingHoles(DebugSetup.HolePattern pattern) {
        var temporary = new DebugSetup(18, 3, pattern, 0, Difficulty.NORMAL, 13).board();
        var setup = new DebugSetup(18, 3, pattern, DebugSetup.FillMode.RANDOM, 0, Difficulty.NORMAL, 13);
        var random = setup.board();
        var seen = new HashSet<Shape>();
        for (int y = 0; y < 20; y++) {
            for (int x = 0; x < 10; x++) {
                assertEquals(temporary[y][x].isEmpty(), random[y][x].isEmpty());
                if (!temporary[y][x].isEmpty()) {
                    assertSame(TemporaryBlock.INSTANCE, temporary[y][x].occupiedBy());
                    assertInstanceOf(TetrominoType.class, random[y][x].occupiedBy());
                    assertFalse(temporary[y][x].hasItem());
                    assertFalse(random[y][x].hasItem());
                    seen.add(random[y][x].occupiedBy());
                }
            }
        }
        assertEquals(new HashSet<>(Arrays.asList(TetrominoType.values())), seen);
        assertArrayEquals(random, setup.board());
        var otherSeed = new DebugSetup(18, 3, pattern, DebugSetup.FillMode.RANDOM, 0, Difficulty.NORMAL, 14);
        assertFalse(Arrays.deepEquals(random, otherSeed.board()));
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
        assertThrows(NullPointerException.class, () -> new DebugSetup(1, 1, DebugSetup.HolePattern.ALIGNED,
                null, 0, Difficulty.NORMAL, 13));
        assertThrows(IllegalArgumentException.class, () -> new DebugSetup(19, 1, DebugSetup.HolePattern.ALIGNED, 0, Difficulty.NORMAL, 13));
        assertThrows(IllegalArgumentException.class, () -> new DebugSetup(1, 11, DebugSetup.HolePattern.ALIGNED, 0, Difficulty.NORMAL, 13));
        assertThrows(IllegalArgumentException.class, () -> new DebugSetup(1, 1, DebugSetup.HolePattern.ALIGNED, -1, Difficulty.NORMAL, 13));
    }
}
