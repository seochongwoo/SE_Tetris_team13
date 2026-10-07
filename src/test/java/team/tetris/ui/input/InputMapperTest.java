package team.tetris.ui.input;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.EnumMap;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import team.tetris.application.GameCommand;
import team.tetris.application.GameStatus;
import team.tetris.application.model.Settings;

class InputMapperTest {

    private final InputMapper defaults = new InputMapper(Settings.defaults());

    @Test
    void mapsDefaultKeysToCommandsWhileRunning() {
        assertEquals(Optional.of(GameCommand.MOVE_LEFT), defaults.commandFor("LEFT", GameStatus.RUNNING));
        assertEquals(Optional.of(GameCommand.MOVE_RIGHT), defaults.commandFor("RIGHT", GameStatus.RUNNING));
        assertEquals(Optional.of(GameCommand.SOFT_DROP), defaults.commandFor("DOWN", GameStatus.RUNNING));
        assertEquals(Optional.of(GameCommand.ROTATE_CW), defaults.commandFor("UP", GameStatus.RUNNING));
        assertEquals(Optional.of(GameCommand.HARD_DROP), defaults.commandFor("SPACE", GameStatus.RUNNING));
        assertEquals(Optional.of(GameCommand.QUIT_GAME), defaults.commandFor("ESCAPE", GameStatus.RUNNING));
        assertEquals(Optional.empty(), defaults.commandFor("X", GameStatus.RUNNING));
    }

    @Test
    void sharedPauseKeyMeansPauseWhileRunningAndResumeWhilePaused() {
        assertEquals(Optional.of(GameCommand.PAUSE), defaults.commandFor("P", GameStatus.RUNNING));
        assertEquals(Optional.of(GameCommand.RESUME), defaults.commandFor("P", GameStatus.PAUSED));
    }

    @Test
    void onlyResumeAndQuitWorkWhilePausedAndNothingAfterTheGameEnds() {
        assertEquals(Optional.empty(), defaults.commandFor("LEFT", GameStatus.PAUSED));
        assertEquals(Optional.of(GameCommand.QUIT_GAME), defaults.commandFor("ESCAPE", GameStatus.PAUSED));
        assertEquals(Optional.empty(), defaults.commandFor("LEFT", GameStatus.GAME_OVER));
        assertEquals(Optional.empty(), defaults.commandFor("P", GameStatus.ABORTED));
    }

    @Test
    void followsCustomKeyBindings() {
        EnumMap<GameCommand, String> keys = new EnumMap<>(Settings.defaults().keyBindings());
        keys.put(GameCommand.MOVE_LEFT, "A");
        InputMapper mapper = new InputMapper(new Settings(Settings.ScreenSize.MEDIUM, keys, false));

        assertEquals(Optional.of(GameCommand.MOVE_LEFT), mapper.commandFor("A", GameStatus.RUNNING));
        assertEquals(Optional.empty(), mapper.commandFor("LEFT", GameStatus.RUNNING));
        assertEquals("A", mapper.keyFor(GameCommand.MOVE_LEFT));
    }

    @Test
    void onlyMovementAndSoftDropRepeatWhileHeld() {
        assertTrue(InputMapper.isRepeatable(GameCommand.MOVE_LEFT));
        assertTrue(InputMapper.isRepeatable(GameCommand.MOVE_RIGHT));
        assertTrue(InputMapper.isRepeatable(GameCommand.SOFT_DROP));
        assertFalse(InputMapper.isRepeatable(GameCommand.ROTATE_CW));
        assertFalse(InputMapper.isRepeatable(GameCommand.HARD_DROP));
        assertFalse(InputMapper.isRepeatable(GameCommand.PAUSE));
    }
}
