package team.tetris.application;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import team.tetris.application.EndGameView.Stage;
import team.tetris.application.port.StorageException;

/** 게임별 종료 결과 보존과 이름 입력·저장·순위 표시 조율. 단일 실행 흐름에서 호출 필요. */
public final class EndGameCoordinator {
    private final ScoreboardService scores;
    private final Map<UUID, Flow> flows = new HashMap<>();

    public EndGameCoordinator(ScoreboardService scores) {
        this.scores = Objects.requireNonNull(scores, "scores");
    }

    public EndGameView begin(GameResult result) {
        Objects.requireNonNull(result, "result");
        Flow flow = flows.computeIfAbsent(result.gameId(), ignored -> new Flow(result));
        if (!flow.result.equals(result)) throw new IllegalArgumentException("Conflicting result for game ID");
        if (flow.view != null && flow.view.storageError().isEmpty()) return flow.view;
        if (result.reason() == GameStatus.ABORTED) return show(flow, Stage.RETURN_MENU, null, null);
        if (flow.registrationResolved) return scoreboard(flow);
        if (flow.view != null && flow.view.stage() == Stage.NAME_REQUIRED) return flow.view;
        try {
            flow.recordId = scores.registeredRecordId(result.gameId());
            if (flow.recordId.isPresent() || !scores.qualifies(result)) {
                flow.registrationResolved = true;
                return scoreboard(flow);
            }
            return show(flow, Stage.NAME_REQUIRED, null, null);
        } catch (StorageException error) {
            return show(flow, Stage.CHECKING, null, error);
        }
    }

    public EndGameView submitName(UUID gameId, String name) {
        Flow flow = flows.get(Objects.requireNonNull(gameId, "gameId"));
        if (flow == null) throw new IllegalArgumentException("Call begin before submitting a name");
        if (flow.registrationResolved) {
            return flow.view.storageError().isPresent() ? scoreboard(flow) : flow.view;
        }
        if (flow.view.stage() == Stage.RETURN_MENU) return flow.view;
        if (flow.view.stage() != Stage.NAME_REQUIRED) throw new IllegalStateException("Registration check pending");
        try {
            flow.recordId = scores.register(flow.result, name == null ? "" : name);
            flow.registrationResolved = true;
            return scoreboard(flow);
        } catch (IllegalArgumentException error) {
            return show(flow, Stage.NAME_REQUIRED, error.getMessage(), null);
        } catch (StorageException error) {
            return show(flow, Stage.NAME_REQUIRED, null, error);
        }
    }

    private EndGameView scoreboard(Flow flow) {
        try {
            var entries = scores.list(flow.result.mode(), flow.result.difficulty());
            var highlight = flow.recordId.filter(id -> entries.stream().anyMatch(e -> e.recordId().equals(id)));
            flow.view = new EndGameView(flow.result.gameId(), Stage.SHOW_SCOREBOARD, entries,
                    highlight, Optional.empty(), Optional.empty());
            return flow.view;
        } catch (StorageException error) {
            return show(flow, Stage.SHOW_SCOREBOARD, null, error);
        }
    }

    private EndGameView show(Flow flow, Stage stage, String nameError, StorageException storageError) {
        flow.view = new EndGameView(flow.result.gameId(), stage, List.of(), Optional.empty(),
                Optional.ofNullable(nameError), Optional.ofNullable(storageError));
        return flow.view;
    }

    private static final class Flow {
        final GameResult result;
        Optional<UUID> recordId = Optional.empty();
        boolean registrationResolved;
        EndGameView view;

        Flow(GameResult result) { this.result = result; }
    }
}
