package team.tetris.ui.input;

import java.awt.event.KeyEvent;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.Map;

/**
 * AWT 키 코드와 설정에 저장하는 키 식별자(예: LEFT, SPACE, P) 사이의 변환.
 *
 * <p>{@code KeyEvent.getKeyText()}는 OS 언어에 따라 결과가 달라지므로 쓰지 않고, KeyEvent의
 * {@code VK_} 상수 이름을 그대로 식별자로 쓴다. 설정 형식({@code [A-Z][A-Z0-9_]*})에 맞추기 위해
 * 숫자 키는 DIGIT0~DIGIT9로 바꾼다.
 */
public final class KeyNames {

    public static final String UP = "UP";
    public static final String DOWN = "DOWN";
    public static final String LEFT = "LEFT";
    public static final String RIGHT = "RIGHT";
    public static final String ENTER = "ENTER";
    public static final String ESCAPE = "ESCAPE";
    public static final String BACK_SPACE = "BACK_SPACE";

    private static final Map<Integer, String> NAMES = buildNames();

    private static final Map<String, String> DISPLAY = Map.ofEntries(
            Map.entry(LEFT, "←"),
            Map.entry(RIGHT, "→"),
            Map.entry(UP, "↑"),
            Map.entry(DOWN, "↓"),
            Map.entry("SPACE", "Space"),
            Map.entry(ESCAPE, "Esc"),
            Map.entry(ENTER, "Enter"),
            Map.entry(BACK_SPACE, "Backspace"),
            Map.entry("SHIFT", "Shift"),
            Map.entry("CONTROL", "Ctrl"),
            Map.entry("ALT", "Alt"),
            Map.entry("TAB", "Tab"));

    private KeyNames() {
    }

    /** 키 코드의 식별자. 알 수 없는 키면 null. */
    public static String nameOf(int keyCode) {
        return NAMES.get(keyCode);
    }

    /** 화면에 보여줄 짧은 표기 (예: LEFT → ←, DIGIT1 → 1). */
    public static String display(String key) {
        if (key == null) {
            return "-";
        }
        String fixed = DISPLAY.get(key);
        if (fixed != null) {
            return fixed;
        }
        if (key.startsWith("DIGIT") && key.length() == 6) {
            return key.substring(5);
        }
        return key;
    }

    private static Map<Integer, String> buildNames() {
        Map<Integer, String> names = new HashMap<>();
        for (Field field : KeyEvent.class.getFields()) {
            if (!field.getName().startsWith("VK_") || field.getType() != int.class
                    || !Modifier.isStatic(field.getModifiers())) {
                continue;
            }
            try {
                int code = field.getInt(null);
                if (code == KeyEvent.VK_UNDEFINED) {
                    continue;
                }
                String name = field.getName().substring(3);
                if (name.chars().allMatch(Character::isDigit)) {
                    name = "DIGIT" + name;
                }
                names.putIfAbsent(code, name);
            } catch (IllegalAccessException ignored) {
                // public static 상수만 읽으므로 발생하지 않는다.
            }
        }
        return Map.copyOf(names);
    }
}
