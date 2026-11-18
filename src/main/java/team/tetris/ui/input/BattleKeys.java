package team.tetris.ui.input;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import team.tetris.application.GameCommand;
import team.tetris.application.battle.BattlePlayer;
import team.tetris.application.model.BattleKeyBindings;
import team.tetris.application.model.Settings;

/**
 * 로컬 대전의 키 배치. 플레이어별 조작 키는 설정의 대전 키({@link BattleKeyBindings}, 카드 18)를, 일시정지·재개·
 * 게임 메뉴는 두 사람이 같이 쓰는 공통 키로 설정의 키를 따른다. 설정 모델이 공통 키와 조작 키가 겹치지 않음을
 * 보장하므로 여기서 대체 키를 고르지 않는다.
 */
public final class BattleKeys {

    /** 대전에서 플레이어가 쓰는 조작. 공통 키(일시정지·메뉴)는 따로 다룬다. */
    public static final List<GameCommand> PLAY_COMMANDS = BattleKeyBindings.COMMANDS;

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

    private BattleKeys(Settings settings) {
        BattleKeyBindings battle = settings.battleKeys();
        keys.put(BattlePlayer.ONE, battle.one());
        keys.put(BattlePlayer.TWO, battle.two());
        for (BattlePlayer player : BattlePlayer.values()) {
            for (GameCommand command : PLAY_COMMANDS) {
                bindings.put(keys.get(player).get(command), new Binding(player, command));
            }
        }
        Map<GameCommand, String> common = settings.keyBindings();
        pauseKey = common.get(GameCommand.PAUSE);
        resumeKey = common.get(GameCommand.RESUME);
        menuKey = common.get(GameCommand.QUIT_GAME);
    }

    /** 설정의 대전 키와 공통 키로 만든 배치. */
    public static BattleKeys from(Settings settings) {
        return new BattleKeys(Objects.requireNonNull(settings, "settings"));
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
