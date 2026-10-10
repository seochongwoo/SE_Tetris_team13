package team.tetris.application.model;

import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import team.tetris.application.GameCommand;

/** UI 독립 키 식별자와 화면 프리셋 보관. 동일 조작 문맥의 키 충돌 검증. */
public record Settings(ScreenSize screenSize, Map<GameCommand, String> keyBindings, boolean colorBlindMode, Difficulty difficulty) {
    public enum ScreenSize { SMALL, MEDIUM, LARGE }

    /** 기존 호출은 보통 난이도를 사용한다. */
    public Settings(ScreenSize screenSize, Map<GameCommand, String> keyBindings, boolean colorBlindMode) {
        this(screenSize, keyBindings, colorBlindMode, Difficulty.NORMAL);
    }

    public Settings {
        Objects.requireNonNull(screenSize, "screenSize");
        Objects.requireNonNull(difficulty, "difficulty");
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
        return new Settings(ScreenSize.MEDIUM, keys, false);
    }
}
