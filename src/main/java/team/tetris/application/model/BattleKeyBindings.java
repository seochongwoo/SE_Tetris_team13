package team.tetris.application.model;

import java.util.Collection;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import team.tetris.application.GameCommand;

/**
 * 로컬 대전의 1P·2P 조작 키 (3차 카드 18, H3-01). 플레이어마다 {@link #COMMANDS} 다섯 조작에 키를 하나씩
 * 둔다. 열 개의 키는 모두 달라야 한다. 한 키가 두 플레이어나 두 조작에 묶이면 누구의 조작인지 정할 수 없다.
 *
 * <p>일시정지·재개·게임 메뉴는 대전에서 두 사람이 같이 쓰는 공통 키라 여기 두지 않고 {@link Settings#keyBindings()}의
 * 키를 쓴다. 공통 키와 조작 키가 겹치지 않는지는 {@link Settings}가 검사한다.
 */
public record BattleKeyBindings(Map<GameCommand, String> one, Map<GameCommand, String> two) {

    /** 대전에서 플레이어가 쓰는 조작. 화면의 키 목록도 이 순서를 따른다. */
    public static final List<GameCommand> COMMANDS = List.of(GameCommand.ROTATE_CW, GameCommand.MOVE_LEFT,
            GameCommand.MOVE_RIGHT, GameCommand.SOFT_DROP, GameCommand.HARD_DROP);

    /** 안건 ⑲의 기본 배치: 1P는 키보드 왼쪽(W A D S Space), 2P는 방향키와 Enter. */
    private static final BattleKeyBindings STANDARD = new BattleKeyBindings(
            keys("W", "A", "D", "S", "SPACE"), keys("UP", "LEFT", "RIGHT", "DOWN", "ENTER"));

    /**
     * 기본 키가 공통 키와 겹칠 때 대신 쓸 키. 플레이어마다 자기 쪽 손이 닿는 키를 순서대로 쓴다.
     * 사용자가 정한 공통 키는 덮어쓰지 않는다 (카드 18).
     */
    private static final List<String> ONE_SPARES = List.of("Q", "E", "R", "F", "Z", "X", "C", "V", "G", "T");
    private static final List<String> TWO_SPARES = List.of("NUMPAD8", "NUMPAD4", "NUMPAD6", "NUMPAD5", "NUMPAD0",
            "SLASH", "PERIOD", "COMMA", "SEMICOLON", "QUOTE");

    public BattleKeyBindings {
        one = copy(one);
        two = copy(two);
        Set<String> used = new HashSet<>();
        for (Map<GameCommand, String> keys : List.of(one, two)) {
            for (GameCommand command : COMMANDS) {
                if (!used.add(keys.get(command))) {
                    throw new IllegalArgumentException("Key " + keys.get(command) + " is used twice in battle");
                }
            }
        }
    }

    private static Map<GameCommand, String> copy(Map<GameCommand, String> keys) {
        Objects.requireNonNull(keys, "keys");
        if (!keys.keySet().equals(Set.copyOf(COMMANDS))) {
            throw new IllegalArgumentException("Battle keys need exactly " + COMMANDS);
        }
        for (String key : keys.values()) {
            if (key == null || !key.matches("[A-Z][A-Z0-9_]*")) {
                throw new IllegalArgumentException("Invalid key identifier");
            }
        }
        return Map.copyOf(keys);
    }

    /** 안건 ⑲의 기본 배치 그대로. */
    public static BattleKeyBindings standard() {
        return STANDARD;
    }

    /**
     * 기본 배치에서 reserved(공통 키)와 겹치는 키만 대체 키로 바꾼 배치. 겹치는 게 없으면 {@link #standard()}.
     * 2차 설정 파일처럼 대전 키가 없는 설정을 불러올 때와 대전 키만 기본값으로 되돌릴 때 쓴다.
     */
    public static BattleKeyBindings defaultsAvoiding(Collection<String> reserved) {
        Set<String> taken = new HashSet<>(reserved);
        STANDARD.one.values().forEach(taken::add);
        STANDARD.two.values().forEach(taken::add);
        return new BattleKeyBindings(avoid(STANDARD.one, reserved, ONE_SPARES, taken),
                avoid(STANDARD.two, reserved, TWO_SPARES, taken));
    }

    private static Map<GameCommand, String> avoid(Map<GameCommand, String> keys, Collection<String> reserved,
                                                  List<String> spares, Set<String> taken) {
        Map<GameCommand, String> result = new EnumMap<>(keys);
        for (GameCommand command : COMMANDS) {
            if (reserved.contains(result.get(command))) {
                String spare = spares.stream().filter(key -> !taken.contains(key)).findFirst()
                        .orElseThrow(() -> new IllegalArgumentException("No spare battle key left"));
                taken.add(spare);
                result.put(command, spare);
            }
        }
        return result;
    }

    /** player의 키. player는 1 또는 2. */
    public Map<GameCommand, String> of(int player) {
        return switch (player) {
            case 1 -> one;
            case 2 -> two;
            default -> throw new IllegalArgumentException("Player must be 1 or 2: " + player);
        };
    }

    /** player의 command 키를 key로 바꾼 배치. 겹치는 키면 IllegalArgumentException. */
    public BattleKeyBindings with(int player, GameCommand command, String key) {
        Map<GameCommand, String> changed = new EnumMap<>(of(player));
        if (changed.put(command, key) == null) {
            throw new IllegalArgumentException("Not a battle command: " + command);
        }
        return player == 1 ? new BattleKeyBindings(changed, two) : new BattleKeyBindings(one, changed);
    }

    /** 이 키를 쓰는 플레이어(1, 2)와 조작. 없으면 null. */
    public Map.Entry<Integer, GameCommand> find(String key) {
        for (int player = 1; player <= 2; player++) {
            for (GameCommand command : COMMANDS) {
                if (of(player).get(command).equals(key)) {
                    return Map.entry(player, command);
                }
            }
        }
        return null;
    }

    private static Map<GameCommand, String> keys(String rotate, String left, String right, String down, String drop) {
        Map<GameCommand, String> keys = new EnumMap<>(GameCommand.class);
        keys.put(GameCommand.ROTATE_CW, rotate);
        keys.put(GameCommand.MOVE_LEFT, left);
        keys.put(GameCommand.MOVE_RIGHT, right);
        keys.put(GameCommand.SOFT_DROP, down);
        keys.put(GameCommand.HARD_DROP, drop);
        return keys;
    }
}
