package team.tetris.core.result;

import java.util.List;
import team.tetris.core.EngineSnapshot;

/**
 * PlayerEngine.apply(GameAction)/tick() 한 번 호출의 결과.
 *
 * <p>dropResult/lockResult/clearResult는 전부 "이번 호출로 그 일이 실제 일어났을 때만" 채워지고,
 * 아니면 null이다 - 예를 들어 좌우 이동/회전만 한 경우엔 셋 다 null이고, 소프트/하드 드롭·자동
 * 하강으로 실제 내려간 경우에만 dropResult가 채워진다. itemActivations는 null이 아니며, 아이템이
 * 발동하지 않았으면 빈 목록이다.
 * 점수를 몇 점 줄지는 여기서 정하지 않는다 - application이 이 사실들을 받아 계산한다.
 *
 * @param snapshot        이번 호출 이후의 최신 상태
 * @param dropResult      이번에 몇 칸 하강했는지 (하강이 없었으면 null)
 * @param lockResult      이번에 블록이 고정됐다면 그 정보 (아니면 null)
 * @param clearResult     이번에 라인이 지워졌다면 그 정보 (아니면 null)
 * @param itemActivations 이번 고정으로 발동한 아이템들 (고정 칸 순서)
 */
public record EngineStep(
        EngineSnapshot snapshot, DropResult dropResult, LockResult lockResult, ClearResult clearResult,
        List<ItemActivation> itemActivations) {

    public EngineStep {
        itemActivations = itemActivations == null ? List.of() : List.copyOf(itemActivations);
    }

    /** 아이템 발동이 없는 결과. */
    public EngineStep(EngineSnapshot snapshot, DropResult dropResult, LockResult lockResult, ClearResult clearResult) {
        this(snapshot, dropResult, lockResult, clearResult, List.of());
    }
}
