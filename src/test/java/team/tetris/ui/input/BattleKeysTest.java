package team.tetris.ui.input;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.EnumMap;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;
import team.tetris.application.GameCommand;
import team.tetris.application.battle.BattlePlayer;
import team.tetris.application.model.Settings;

/** 로컬 대전 키 배치 (3차 안건 ⑲). */
class BattleKeysTest {

    @Test
    void theDefaultLayoutGivesEachPlayerTheirOwnKeys() {
        BattleKeys keys = BattleKeys.from(Settings.defaults());

        assertEquals("W", keys.keyFor(BattlePlayer.ONE, GameCommand.ROTATE_CW));
        assertEquals("A", keys.keyFor(BattlePlayer.ONE, GameCommand.MOVE_LEFT));
        assertEquals("D", keys.keyFor(BattlePlayer.ONE, GameCommand.MOVE_RIGHT));
        assertEquals("S", keys.keyFor(BattlePlayer.ONE, GameCommand.SOFT_DROP));
        assertEquals("SPACE", keys.keyFor(BattlePlayer.ONE, GameCommand.HARD_DROP));
        assertEquals(KeyNames.UP, keys.keyFor(BattlePlayer.TWO, GameCommand.ROTATE_CW));
        assertEquals(KeyNames.LEFT, keys.keyFor(BattlePlayer.TWO, GameCommand.MOVE_LEFT));
        assertEquals(KeyNames.RIGHT, keys.keyFor(BattlePlayer.TWO, GameCommand.MOVE_RIGHT));
        assertEquals(KeyNames.DOWN, keys.keyFor(BattlePlayer.TWO, GameCommand.SOFT_DROP));
        assertEquals(KeyNames.ENTER, keys.keyFor(BattlePlayer.TWO, GameCommand.HARD_DROP));
    }

    @Test
    void everyPlayKeyMapsBackToItsPlayerAndCommandWithoutOverlap() {
        BattleKeys keys = BattleKeys.from(Settings.defaults());
        Set<String> used = new HashSet<>();

        for (BattlePlayer player : BattlePlayer.values()) {
            for (GameCommand command : BattleKeys.PLAY_COMMANDS) {
                String key = keys.keyFor(player, command);
                assertTrue(used.add(key), key);
                assertEquals(new BattleKeys.Binding(player, command), keys.bindingFor(key).orElseThrow());
            }
        }
        assertTrue(keys.bindingFor("P").isEmpty());
        assertTrue(keys.bindingFor("Q").isEmpty());
    }

    @Test
    void commonKeysFollowTheSettings() {
        var bindings = new EnumMap<>(Settings.defaults().keyBindings());
        bindings.put(GameCommand.PAUSE, "F1");
        bindings.put(GameCommand.RESUME, "F1");
        bindings.put(GameCommand.QUIT_GAME, "Q");
        BattleKeys keys = BattleKeys.from(new Settings(Settings.ScreenSize.MEDIUM, bindings, false));

        assertEquals("F1", keys.pauseKey());
        assertEquals("F1", keys.resumeKey());
        assertEquals("Q", keys.menuKey());
    }

    @Test
    void aCommonKeyOnADefaultBattleKeyKeepsTheUsersKeyAndMovesTheBattleKey() {
        // 카드 18: 1인 게임에서 일시정지를 W로 바꿨다면 그 키를 덮어쓰지 않고, 1P 회전에 대체 키(Q)를 쓴다.
        var bindings = new EnumMap<>(Settings.defaults().keyBindings());
        bindings.put(GameCommand.PAUSE, "W");
        bindings.put(GameCommand.RESUME, "W");
        BattleKeys keys = BattleKeys.from(new Settings(Settings.ScreenSize.MEDIUM, bindings, false));

        assertEquals("W", keys.pauseKey());
        assertEquals("W", keys.resumeKey());
        assertTrue(keys.bindingFor("W").isEmpty());
        assertEquals("Q", keys.keyFor(BattlePlayer.ONE, GameCommand.ROTATE_CW));
        assertEquals(new BattleKeys.Binding(BattlePlayer.ONE, GameCommand.ROTATE_CW), keys.bindingFor("Q").orElseThrow());
    }

    @Test
    void changedBattleKeysComeFromTheSettings() {
        Settings settings = Settings.defaults().withBattleKeys(Settings.defaults().battleKeys()
                .with(1, GameCommand.HARD_DROP, "F").with(2, GameCommand.HARD_DROP, "SHIFT"));
        BattleKeys keys = BattleKeys.from(settings);

        assertEquals("F", keys.keyFor(BattlePlayer.ONE, GameCommand.HARD_DROP));
        assertEquals(new BattleKeys.Binding(BattlePlayer.TWO, GameCommand.HARD_DROP), keys.bindingFor("SHIFT").orElseThrow());
        assertTrue(keys.bindingFor("SPACE").isEmpty());
        assertTrue(keys.bindingFor(KeyNames.ENTER).isEmpty());
    }
}
