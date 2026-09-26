package team.tetris.application;

import static org.junit.jupiter.api.Assertions.*;
import java.util.UUID;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

class GameResultTest {
    @ParameterizedTest
    @EnumSource(value = GameStatus.class, names = {"RUNNING", "PAUSED"})
    void unfinishedGameCannotBecomeAResult(GameStatus reason) {
        assertThrows(IllegalArgumentException.class,
                () -> new GameResult(UUID.randomUUID(), 0, 0, 0, reason));
    }

    @ParameterizedTest
    @CsvSource({"-1,0,0", "0,-1,0", "0,0,-1"})
    void invalidStatisticsCannotEnterEndingFlow(long score, int level, int lines) {
        assertThrows(IllegalArgumentException.class,
                () -> new GameResult(UUID.randomUUID(), score, level, lines, GameStatus.GAME_OVER));
    }
}
