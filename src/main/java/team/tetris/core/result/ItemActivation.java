package team.tetris.core.result;

import java.util.Objects;
import team.tetris.core.Position;
import team.tetris.core.item.Item;

/**
 * 블록 고정으로 아이템 하나가 발동했다는 사실. position은 아이템이 실린 칸이 고정된 보드 좌표다.
 * 점수 배율처럼 보드 밖에 영향을 주는 아이템은 application이 이 보고를 보고 처리한다.
 */
public record ItemActivation(Item item, Position position) {

    public ItemActivation {
        Objects.requireNonNull(item, "item");
        Objects.requireNonNull(position, "position");
    }
}
