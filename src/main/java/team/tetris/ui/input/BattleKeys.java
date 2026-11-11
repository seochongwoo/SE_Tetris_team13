package team.tetris.ui.input;

import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import team.tetris.application.GameCommand;
import team.tetris.application.battle.BattlePlayer;
import team.tetris.application.model.Settings;

/**
 * 로컬 대전의 키 배치 (3차 안건 ⑲). 1P는 W 회전, A·D 이동, S 내리기, Space 떨어뜨리기, 2P는 ↑ 회전, ←→
 * 이동, ↓ 내리기, Enter 떨어뜨리기를 쓴다. 일시정지·재개·게임 메뉴 키는 두 사람이 같이 쓰며 설정의 키를
 * 따른다.
 *
 * <p>2P 키 설정(카드 18)이 들어오기 전까지 조작 키는 위의 기본값으로 고정한다. 설정의 공통 키가 조작
 * 키와 겹치면 대전에서는 그 공통 키 대신 기본값(P, Esc)을 쓴다. 그래야 누구의 조작도 막히지 않는다.
 */
public final class BattleKeys {

    /** 대전에서 플레이어가 쓰는 조작. 공통 키(일시정지·메뉴)는 따로 다룬다. */
    public static final List<GameCommand> PLAY_COMMANDS = List.of(GameCommand.MOVE_LEFT, GameCommand.MOVE_RIGHT,
            GameCommand.ROTATE_CW, GameCommand.SOFT_DROP, GameCommand.HARD_DROP);

    /** 키 하나가 뜻하는 플레이어의 조작. */
    public record Binding(BattlePlayer player, GameCommand command) {
        public Binding {
            Objects.requireNonNull(player, "player");
            Objects.requireNonNull(command, "command");
        }
    }

    private final Map<BattlePlayer, Map<GameCommand, String>> keys = new EnumMap<>(BattlePlayer.class);
    private final Map<String, Binding> bindings = new HashMap<>();
    private final String pauseKey;
    private final String resumeKey;
    private final String menuKey;

    private BattleKeys(Map<GameCommand, String> one, Map<GameCommand, String> two, Settings settings) {
        keys.put(BattlePlayer.ONE, Collections.unmodifiableMap(new EnumMap<>(one)));
        keys.put(BattlePlayer.TWO, Collections.unmodifiableMap(new EnumMap<>(two)));
        for (BattlePlayer player : BattlePlayer.values()) {
            for (GameCommand command : PLAY_COMMANDS) {
                String key = Objects.requireNonNull(keys.get(player).get(command), "key");
                Binding previous = bindings.put(key, new Binding(player, command));
                if (previous != null) {
                    throw new IllegalArgumentException("Key " + key + " is used twice");
                }
            }
        }
        Map<GameCommand, String> saved = settings.keyBindings();
        boolean clash = bindings.containsKey(saved.get(GameCommand.PAUSE))
                || bindings.containsKey(saved.get(GameCommand.RESUME))
                || bindings.containsKey(saved.get(GameCommand.QUIT_GAME));
        Map<GameCommand, String> common = clash ? Settings.defaults().keyBindings() : saved;
        pauseKey = common.get(GameCommand.PAUSE);
        resumeKey = common.get(GameCommand.RESUME);
        menuKey = common.get(GameCommand.QUIT_GAME);
    }

    /** 안건 ⑲의 기본 배치. 공통 키는 settings에서 가져온다. */
    public static BattleKeys defaults(Settings settings) {
        Map<GameCommand, String> one = new EnumMap<>(GameCommand.class);
        one.put(GameCommand.ROTATE_CW, "W");
        one.put(GameCommand.MOVE_LEFT, "A");
        one.put(GameCommand.MOVE_RIGHT, "D");
        one.put(GameCommand.SOFT_DROP, "S");
        one.put(GameCommand.HARD_DROP, "SPACE");
        Map<GameCommand, String> two = new EnumMap<>(GameCommand.class);
        two.put(GameCommand.ROTATE_CW, KeyNames.UP);
        two.put(GameCommand.MOVE_LEFT, KeyNames.LEFT);
        two.put(GameCommand.MOVE_RIGHT, KeyNames.RIGHT);
        two.put(GameCommand.SOFT_DROP, KeyNames.DOWN);
        two.put(GameCommand.HARD_DROP, KeyNames.ENTER);
        return new BattleKeys(one, two, Objects.requireNonNull(settings, "settings"));
    }

    /** 조작 키면 그 플레이어와 조작. 공통 키나 모르는 키면 empty. */
    public Optional<Binding> bindingFor(String key) {
        return Optional.ofNullable(bindings.get(key));
    }

    /** player의 command에 묶인 키 식별자. */
    public String keyFor(BattlePlayer player, GameCommand command) {
        return keys.get(player).get(command);
    }

    public String pauseKey() {
        return pauseKey;
    }

    public String resumeKey() {
        return resumeKey;
    }

    /** 게임 메뉴 키(기본 Esc). 진행 중에는 일시정지 메뉴를 연다. */
    public String menuKey() {
        return menuKey;
    }
}
