package team.tetris.application.model;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import org.junit.jupiter.api.Test;
import team.tetris.application.GameCommand;

/** 대전 1P·2P 조작 키와 공통 키의 관계 (3차 카드 18, 안건 ⑲, H3-01). */
class BattleKeyBindingsTest {

    private static Map<GameCommand, String> keys(String rotate, String left, String right, String down, String drop) {
        Map<GameCommand, String> keys = new EnumMap<>(GameCommand.class);
        keys.put(GameCommand.ROTATE_CW, rotate);
        keys.put(GameCommand.MOVE_LEFT, left);
        keys.put(GameCommand.MOVE_RIGHT, right);
        keys.put(GameCommand.SOFT_DROP, down);
        keys.put(GameCommand.HARD_DROP, drop);
        return keys;
    }

    private static Map<GameCommand, String> common(String pause, String resume, String menu) {
        Map<GameCommand, String> keys = new EnumMap<>(Settings.defaults().keyBindings());
        keys.put(GameCommand.PAUSE, pause);
        keys.put(GameCommand.RESUME, resume);
        keys.put(GameCommand.QUIT_GAME, menu);
        return keys;
    }

    @Test
    void theDefaultsAreAgendaNineteen() {
        BattleKeyBindings standard = BattleKeyBindings.standard();
        assertEquals(keys("W", "A", "D", "S", "SPACE"), standard.one());
        assertEquals(keys("UP", "LEFT", "RIGHT", "DOWN", "ENTER"), standard.two());
        assertSame(standard, Settings.defaults().battleKeys());
        assertEquals(new BattleKeyBindings(standard.one(), standard.two()), standard);
    }

    @Test
    void aKeyCanBelongToOnlyOnePlayerAndOneCommand() {
        Map<GameCommand, String> one = keys("W", "A", "D", "S", "SPACE");
        // 2P가 1P의 키를 쓰면 누구의 조작인지 정할 수 없다.
        assertThrows(IllegalArgumentException.class,
                () -> new BattleKeyBindings(one, keys("UP", "A", "RIGHT", "DOWN", "ENTER")));
        assertThrows(IllegalArgumentException.class,
                () -> new BattleKeyBindings(keys("W", "A", "A", "S", "SPACE"), BattleKeyBindings.standard().two()));
        assertThrows(IllegalArgumentException.class,
                () -> BattleKeyBindings.standard().with(2, GameCommand.HARD_DROP, "SPACE"));
        assertThrows(IllegalArgumentException.class,
                () -> BattleKeyBindings.standard().with(1, GameCommand.MOVE_LEFT, "D"));
    }

    @Test
    void everyBattleCommandNeedsAValidKeyAndNothingElse() {
        Map<GameCommand, String> missing = keys("W", "A", "D", "S", "SPACE");
        missing.remove(GameCommand.HARD_DROP);
        assertThrows(IllegalArgumentException.class, () -> new BattleKeyBindings(missing, BattleKeyBindings.standard().two()));
        Map<GameCommand, String> extra = keys("W", "A", "D", "S", "SPACE");
        extra.put(GameCommand.PAUSE, "P");
        assertThrows(IllegalArgumentException.class, () -> new BattleKeyBindings(extra, BattleKeyBindings.standard().two()));
        assertThrows(IllegalArgumentException.class,
                () -> new BattleKeyBindings(keys("w", "A", "D", "S", "SPACE"), BattleKeyBindings.standard().two()));
        assertThrows(IllegalArgumentException.class,
                () -> BattleKeyBindings.standard().with(1, GameCommand.PAUSE, "F"));
        assertThrows(IllegalArgumentException.class, () -> BattleKeyBindings.standard().of(3));
    }

    @Test
    void changingOneKeyKeepsTheOthersAndFindTellsWhoUsesAKey() {
        BattleKeyBindings changed = BattleKeyBindings.standard().with(2, GameCommand.HARD_DROP, "SHIFT");

        assertEquals("SHIFT", changed.two().get(GameCommand.HARD_DROP));
        assertEquals(BattleKeyBindings.standard().one(), changed.one());
        assertEquals(Map.entry(2, GameCommand.HARD_DROP), changed.find("SHIFT"));
        assertEquals(Map.entry(1, GameCommand.ROTATE_CW), changed.find("W"));
        assertNull(changed.find(team.tetris.application.model.Settings.defaults().keyBindings().get(GameCommand.PAUSE)));
        assertNull(changed.find("ENTER"));
    }

    @Test
    void commonKeysCannotBeBattleKeysInEitherDirection() {
        Settings defaults = Settings.defaults();
        // 1인 게임의 일시정지를 1P 회전 키로 바꾸면 대전에서 회전과 일시정지가 겹친다.
        assertThrows(IllegalArgumentException.class, () -> defaults.withKeyBindings(common("W", "W", "ESCAPE")));
        assertThrows(IllegalArgumentException.class, () -> defaults.withKeyBindings(common("P", "P", "ENTER")));
        // 대전 키를 공통 키로 바꾸는 것도 같은 이유로 거부한다.
        assertThrows(IllegalArgumentException.class,
                () -> defaults.withBattleKeys(defaults.battleKeys().with(1, GameCommand.HARD_DROP, "P")));
        assertThrows(IllegalArgumentException.class,
                () -> defaults.withBattleKeys(defaults.battleKeys().with(2, GameCommand.HARD_DROP, "ESCAPE")));
        // 1인 게임 조작 키는 대전과 상관없다. 2P와 같은 방향키를 그대로 쓴다.
        assertEquals("LEFT", defaults.keyBindings().get(GameCommand.MOVE_LEFT));
        Map<GameCommand, String> single = new EnumMap<>(defaults.keyBindings());
        single.put(GameCommand.HARD_DROP, "W");
        assertDoesNotThrow(() -> defaults.withKeyBindings(single));
    }

    @Test
    void defaultsKeepTheUsersCommonKeysAndMoveOnlyTheClashingBattleKeys() {
        // 2차 설정에서 일시정지를 S, 게임 메뉴를 Enter로 바꾼 사용자 (카드 18의 충돌 정책).
        Settings legacy = new Settings(Settings.ScreenSize.MEDIUM, common("S", "S", "ENTER"), false);

        assertEquals("S", legacy.keyBindings().get(GameCommand.PAUSE));
        assertEquals("ENTER", legacy.keyBindings().get(GameCommand.QUIT_GAME));
        assertEquals(keys("W", "A", "D", "Q", "SPACE"), legacy.battleKeys().one());
        assertEquals(keys("UP", "LEFT", "RIGHT", "DOWN", "NUMPAD8"), legacy.battleKeys().two());
        // 겹치는 공통 키가 없으면 안건 ⑲ 배치 그대로.
        assertEquals(BattleKeyBindings.standard(),
                new Settings(Settings.ScreenSize.MEDIUM, Settings.defaults().keyBindings(), false).battleKeys());
    }

    @Test
    void anyValidCommonKeysGiveAValidBattleLayout() {
        // 공통 키 세 개를 기본 키·대체 키에서 아무렇게나 골라도 겹치지 않는 대전 배치가 나온다.
        List<String> pool = new ArrayList<>(List.of("W", "A", "D", "S", "SPACE", "UP", "LEFT", "RIGHT", "DOWN",
                "ENTER", "Q", "E", "R", "NUMPAD8", "NUMPAD4", "P", "ESCAPE", "F1"));
        Random random = new Random(18);
        for (int i = 0; i < 2000; i++) {
            String pause = pool.get(random.nextInt(pool.size()));
            String resume = random.nextBoolean() ? pause : pool.get(random.nextInt(pool.size()));
            String menu = pool.get(random.nextInt(pool.size()));
            if (menu.equals(pause)) {
                continue;
            }
            Map<GameCommand, String> bindings = common(pause, resume, menu);
            Settings settings;
            try {
                settings = new Settings(Settings.ScreenSize.SMALL, bindings, false);
            } catch (IllegalArgumentException conflictInSinglePlayer) {
                continue; // 1인 게임 키끼리 겹치는 조합은 원래부터 저장할 수 없다.
            }
            Set<String> battle = new HashSet<>(settings.battleKeys().one().values());
            battle.addAll(settings.battleKeys().two().values());
            assertEquals(10, battle.size());
            for (String key : Settings.commonKeys(bindings)) {
                assertFalse(battle.contains(key), key);
            }
            for (GameCommand command : BattleKeyBindings.COMMANDS) {
                String standard = BattleKeyBindings.standard().one().get(command);
                if (!Settings.commonKeys(bindings).contains(standard)) {
                    assertEquals(standard, settings.battleKeys().one().get(command), "겹치지 않는 키는 그대로");
                }
            }
        }
        assertTrue(Settings.COMMON_COMMANDS.containsAll(List.of(GameCommand.PAUSE, GameCommand.RESUME,
                GameCommand.QUIT_GAME)));
    }
}
