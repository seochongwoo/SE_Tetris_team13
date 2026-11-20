package team.tetris.application.model;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import team.tetris.application.GameCommand;

/**
 * UI 독립 키 식별자와 화면 프리셋 보관. 동일 조작 문맥의 키 충돌 검증.
 *
 * <p>keyBindings는 1인 게임의 조작 키와 공통 키(일시정지·재개·게임 메뉴)다. battleKeys는 로컬 대전의 1P·2P
 * 조작 키이며, 대전에서도 공통 키는 keyBindings의 것을 같이 쓴다. 그래서 대전 조작 키는 공통 키와 겹칠 수
 * 없다 (카드 18, 안건 ⑲).
 */
public record Settings(ScreenSize screenSize, Map<GameCommand, String> keyBindings, boolean colorBlindMode,
                       Difficulty difficulty, BattleKeyBindings battleKeys) {
    public enum ScreenSize { SMALL, MEDIUM, LARGE }

    /** 대전에서 두 사람이 같이 쓰는 조작. */
    public static final List<GameCommand> COMMON_COMMANDS =
            List.of(GameCommand.PAUSE, GameCommand.RESUME, GameCommand.QUIT_GAME);

    /** 기존 호출은 보통 난이도를 사용한다. */
    public Settings(ScreenSize screenSize, Map<GameCommand, String> keyBindings, boolean colorBlindMode) {
        this(screenSize, keyBindings, colorBlindMode, Difficulty.NORMAL);
    }

    /** 대전 키를 따로 정하지 않으면 기본 배치에서 공통 키와 겹치는 키만 대체 키로 바꿔 쓴다. */
    public Settings(ScreenSize screenSize, Map<GameCommand, String> keyBindings, boolean colorBlindMode,
                    Difficulty difficulty) {
        this(screenSize, keyBindings, colorBlindMode, difficulty,
                BattleKeyBindings.defaultsAvoiding(commonKeys(keyBindings)));
    }

    public Settings {
        Objects.requireNonNull(screenSize, "screenSize");
        Objects.requireNonNull(difficulty, "difficulty");
        Objects.requireNonNull(battleKeys, "battleKeys");
        keyBindings = Map.copyOf(keyBindings);
        if (keyBindings.size() != GameCommand.values().length) {
            throw new IllegalArgumentException("Every game command requires a key");
        }
        for (GameCommand command : GameCommand.values()) {
            String key = keyBindings.get(command);
            if (key == null || !key.matches("[A-Z][A-Z0-9_]*")) {
                throw new IllegalArgumentException("Invalid key identifier");
            }
            for (GameCommand other : GameCommand.values()) {
                if (command != other && sameContext(command, other) && key.equals(keyBindings.get(other))) {
                    throw new IllegalArgumentException("Conflicting keys: " + command + " and " + other);
                }
            }
        }
        for (String key : commonKeys(keyBindings)) {
            if (battleKeys.find(key) != null) {
                throw new IllegalArgumentException("Common key " + key + " is a battle key");
            }
        }
    }

    /** 공통 키(일시정지·재개·게임 메뉴). */
    public static List<String> commonKeys(Map<GameCommand, String> keyBindings) {
        return COMMON_COMMANDS.stream().map(command -> {
            String key = keyBindings.get(command);
            if (key == null) {
                throw new IllegalArgumentException("Every game command requires a key");
            }
            return key;
        }).toList();
    }

    public Settings withScreenSize(ScreenSize value) {
        return new Settings(value, keyBindings, colorBlindMode, difficulty, battleKeys);
    }

    public Settings withColorBlindMode(boolean value) {
        return new Settings(screenSize, keyBindings, value, difficulty, battleKeys);
    }

    public Settings withDifficulty(Difficulty value) {
        return new Settings(screenSize, keyBindings, colorBlindMode, value, battleKeys);
    }

    public Settings withKeyBindings(Map<GameCommand, String> value) {
        return new Settings(screenSize, value, colorBlindMode, difficulty, battleKeys);
    }

    public Settings withBattleKeys(BattleKeyBindings value) {
        return new Settings(screenSize, keyBindings, colorBlindMode, difficulty, value);
    }

    private static boolean sameContext(GameCommand a, GameCommand b) {
        if (a == GameCommand.RESUME || b == GameCommand.RESUME) {
            return a == GameCommand.QUIT_GAME || b == GameCommand.QUIT_GAME;
        }
        return true;
    }

    /** 기본 설정의 단일 정의. 실제 키 이벤트와 픽셀 크기 변환은 UI 책임. */
    public static Settings defaults() {
        var keys = new EnumMap<GameCommand, String>(GameCommand.class);
        keys.put(GameCommand.MOVE_LEFT, "LEFT");
        keys.put(GameCommand.MOVE_RIGHT, "RIGHT");
        keys.put(GameCommand.SOFT_DROP, "DOWN");
        keys.put(GameCommand.ROTATE_CW, "UP");
        keys.put(GameCommand.HARD_DROP, "SPACE");
        keys.put(GameCommand.PAUSE, "P");
        keys.put(GameCommand.RESUME, "P");
        keys.put(GameCommand.QUIT_GAME, "ESCAPE");
        return new Settings(ScreenSize.MEDIUM, keys, false, Difficulty.NORMAL, BattleKeyBindings.standard());
    }
}
