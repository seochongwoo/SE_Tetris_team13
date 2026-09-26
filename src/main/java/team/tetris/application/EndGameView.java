package team.tetris.application;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import team.tetris.application.model.ScoreEntry;
import team.tetris.application.port.StorageException;

/** 종료 화면 상태와 표시 데이터. 실제 화면 전환은 UI 책임. */
public record EndGameView(UUID gameId, Stage stage, List<ScoreEntry> scores,
                          Optional<UUID> highlightedRecordId, Optional<String> nameError,
                          Optional<StorageException> storageError) {
    public enum Stage { CHECKING, NAME_REQUIRED, SHOW_SCOREBOARD, RETURN_MENU }

    public EndGameView {
        Objects.requireNonNull(gameId, "gameId");
        Objects.requireNonNull(stage, "stage");
        scores = List.copyOf(scores);
        Objects.requireNonNull(highlightedRecordId, "highlightedRecordId");
        Objects.requireNonNull(nameError, "nameError");
        Objects.requireNonNull(storageError, "storageError");
    }
}
