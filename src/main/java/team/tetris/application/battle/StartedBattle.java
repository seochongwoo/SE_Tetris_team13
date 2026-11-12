package team.tetris.application.battle;

import java.util.Objects;
import team.tetris.application.model.LoadResult;
import team.tetris.application.model.Settings;

/** 새 대전과 그 대전에 적용한 설정·읽기 오류. */
public record StartedBattle(BattleSession session, LoadResult<Settings> settings) {
    public StartedBattle {
        Objects.requireNonNull(session, "session");
        Objects.requireNonNull(settings, "settings");
    }
}
