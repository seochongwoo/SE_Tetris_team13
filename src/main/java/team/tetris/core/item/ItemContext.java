package team.tetris.core.item;

import team.tetris.core.Cell;
import team.tetris.core.Position;

/**
 * 아이템 효과가 보드에 할 수 있는 일. 엔진이 블록을 고정할 때 만들어 {@link Item#onLock}에 넘긴다.
 *
 * <p>아이템이 보드를 직접 들고 있지 않게 해서, 효과가 할 수 있는 조작을 여기 정의된 것으로
 * 제한한다. 새 아이템에 필요한 조작이 생기면 여기에 메서드를 추가한다.
 */
public interface ItemContext {

    int width();

    int height();

    /** position의 칸. 보드 위쪽(y &lt; 0)은 빈 칸으로 본다. */
    Cell cellAt(Position position);

    /**
     * row 줄을 꽉 차 있지 않아도 이번 고정의 줄 삭제에 포함한다. 실제 삭제는 모든 아이템 효과가
     * 끝난 뒤 꽉 찬 줄과 함께 한 번에 일어난다.
     */
    void clearRow(int row);

    /** position 칸 하나를 비운다. 위 칸들은 내려오지 않는다. */
    void removeCell(Position position);
}
