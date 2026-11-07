package team.tetris.core.rule;

import java.util.List;
import team.tetris.core.Cell;

/**
 * 블록이 고정된 뒤 다음 블록이 나오기 전에 보드 아래에 넣을 줄을 공급한다. 대전에서는 상대의 공격이
 * 쌓인 대기열이 이 역할을 하고, 1인 게임은 {@link #NONE}을 쓴다.
 *
 * <p>엔진은 줄 삭제가 끝난 뒤 한 번 {@link #take()}를 호출한다. 받은 줄은 위에서 아래 순서로 보드
 * 맨 아래에 붙는다.
 */
@FunctionalInterface
public interface IncomingLines {

    /** 들어올 줄이 없는 공급자 (1인 게임). */
    IncomingLines NONE = List::of;

    /**
     * 지금 넣을 줄을 위에서 아래 순서로 꺼낸다. 꺼낸 줄은 공급자에서 사라진다. 줄마다 보드 너비만큼의
     * 칸을 담는다. 넣을 줄이 없으면 빈 목록.
     */
    List<List<Cell>> take();
}
