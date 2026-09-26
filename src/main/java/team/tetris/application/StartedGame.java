package team.tetris.application;

import java.util.Objects;
import team.tetris.application.model.LoadResult;
import team.tetris.application.model.Settings;

/** 새 세션과 해당 판에 적용할 설정·읽기 오류 전달. */
public record StartedGame(GameSession session, LoadResult<Settings> settings) {
    public StartedGame {
        Objects.requireNonNull(session, "session");
        Objects.requireNonNull(settings, "settings");
    }
}
