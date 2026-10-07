package team.tetris.ui.input;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import team.tetris.application.GameCommand;
import team.tetris.application.GameStatus;
import team.tetris.application.model.Settings;

/**
 * 설정의 키 매핑으로 키 식별자를 {@link GameCommand}로 바꾼다.
 *
 * <p>한 키에 여러 명령이 묶일 수 있다 (기본값에서 PAUSE와 RESUME이 둘 다 P). 그래서 현재 게임
 * 상태를 함께 받아, RUNNING에서는 RESUME을 빼고 PAUSED에서는 RESUME·QUIT_GAME만 남긴다.
 * 설정 모델이 같은 문맥 안의 키 충돌을 이미 막으므로 결과는 항상 하나다.
 */
public final class InputMapper {

    private static final Set<GameCommand> REPEATABLE =
            EnumSet.of(GameCommand.MOVE_LEFT, GameCommand.MOVE_RIGHT, GameCommand.SOFT_DROP);

    private final Map<GameCommand, String> bindings;
    private final Map<String, List<GameCommand>> commandsByKey;

    public InputMapper(Settings settings) {
        this.bindings = new EnumMap<>(settings.keyBindings());
        Map<String, List<GameCommand>> byKey = new HashMap<>();
        for (GameCommand command : GameCommand.values()) {
            byKey.computeIfAbsent(bindings.get(command), ignored -> new ArrayList<>()).add(command);
        }
        this.commandsByKey = byKey;
    }

    /** status에서 key가 뜻하는 명령. 해당 상태에서 쓸 수 없는 키면 empty. */
    public Optional<GameCommand> commandFor(String key, GameStatus status) {
        for (GameCommand command : commandsByKey.getOrDefault(key, List.of())) {
            if (allowed(command, status)) {
                return Optional.of(command);
            }
        }
        return Optional.empty();
    }

    /** 키를 누르고 있으면 반복해서 발생시킬 명령인가 (좌우 이동, 소프트드롭). */
    public static boolean isRepeatable(GameCommand command) {
        return REPEATABLE.contains(command);
    }

    /** command에 묶인 키 식별자. */
    public String keyFor(GameCommand command) {
        return bindings.get(command);
    }

    private static boolean allowed(GameCommand command, GameStatus status) {
        return switch (status) {
            case RUNNING -> command != GameCommand.RESUME;
            case PAUSED -> command == GameCommand.RESUME || command == GameCommand.QUIT_GAME;
            case GAME_OVER, ABORTED -> false;
        };
    }
}
