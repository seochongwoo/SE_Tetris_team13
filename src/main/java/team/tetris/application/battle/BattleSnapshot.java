package team.tetris.application.battle;

import java.util.List;
import java.util.Objects;
import team.tetris.application.GameSnapshot;
import team.tetris.application.model.Difficulty;
import team.tetris.application.model.GameMode;
import team.tetris.core.Cell;

/**
 * 대전 화면 한 장에 필요한 상태 (카드 19의 화면이 그린다).
 *
 * @param one        1P의 보드·블록·점수
 * @param two        2P의 보드·블록·점수
 * @param pendingOne 1P 보드에 들어가기를 기다리는 공격 줄 (위에서 아래 순서)
 * @param pendingTwo 2P 보드에 들어가기를 기다리는 공격 줄
 * @param status     대전 전체 상태
 * @param result     끝났으면 결과, 진행 중이면 null
 * @param timer      시간제한 대전의 시계, 시간제한이 없으면 null
 */
public record BattleSnapshot(GameSnapshot one, GameSnapshot two, List<List<Cell>> pendingOne,
                             List<List<Cell>> pendingTwo, BattleStatus status, BattleResult result,
                             GameMode mode, Difficulty difficulty, BattleTimer timer) {

    /** 시간제한이 없는 대전. */
    public BattleSnapshot(GameSnapshot one, GameSnapshot two, List<List<Cell>> pendingOne,
                          List<List<Cell>> pendingTwo, BattleStatus status, BattleResult result,
                          GameMode mode, Difficulty difficulty) {
        this(one, two, pendingOne, pendingTwo, status, result, mode, difficulty, null);
    }

    public BattleSnapshot {
        Objects.requireNonNull(one, "one");
        Objects.requireNonNull(two, "two");
        pendingOne = pendingOne.stream().map(List::copyOf).toList();
        pendingTwo = pendingTwo.stream().map(List::copyOf).toList();
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(mode, "mode");
        Objects.requireNonNull(difficulty, "difficulty");
        if ((status == BattleStatus.FINISHED) != (result != null)) {
            throw new IllegalArgumentException("A result is required exactly when the battle is finished");
        }
    }

    public GameSnapshot of(BattlePlayer player) {
        return player == BattlePlayer.ONE ? one : two;
    }

    public List<List<Cell>> pendingFor(BattlePlayer player) {
        return player == BattlePlayer.ONE ? pendingOne : pendingTwo;
    }
}
