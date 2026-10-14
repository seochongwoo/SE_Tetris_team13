package team.tetris.core.item;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import team.tetris.core.Cell;
import team.tetris.core.Position;
import team.tetris.core.Shape;
import team.tetris.core.TetrominoType;

/**
 * 연쇄 삭제 아이템 {@code C}. 일반 블록의 한 칸에 붙는다.
 *
 * <p>블록이 고정되면 아이템 칸에서 시작해 상하좌우로 이어진 같은 종류(같은 테트로미노 모양)의 칸
 * 덩어리를 모두 지운다. 고정된 블록 자신도 덩어리에 포함된다. 다만 이웃에 같은 종류의 칸이 하나도
 * 없으면(덩어리가 블록 자신뿐이면) 발동만 하고 아무것도 지우지 않는다.
 *
 * <p>칸에는 블록 개체 구분이 없으므로, 따로 놓인 같은 종류의 블록 둘이 맞닿아 있으면 한 덩어리로 본다.
 * 무게추처럼 테트로미노가 아닌 칸은 덩어리에 넣지 않는다. 지운 칸 위의 칸은 내려오지 않는다.
 */
public final class ChainClearItem implements Item {

    public static final char SYMBOL = 'C';

    private static final int[][] NEIGHBORS = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};

    @Override
    public char symbol() {
        return SYMBOL;
    }

    @Override
    public void onLock(ItemContext context, Position position) {
        Cell start = context.cellAt(position);
        if (!(start.occupiedBy() instanceof TetrominoType type)) {
            return;
        }
        List<Position> group = connectedGroup(context, position, type);
        if (group.size() <= type.cellCount()) {
            return; // 같은 종류의 이웃이 없음
        }
        for (Position cell : group) {
            context.removeCell(cell);
        }
    }

    /** start에서 상하좌우로 이어진, type 모양으로 채워진 칸들 (너비 우선 탐색). */
    private static List<Position> connectedGroup(ItemContext context, Position start, Shape type) {
        List<Position> group = new ArrayList<>();
        Set<Position> visited = new HashSet<>();
        Deque<Position> queue = new ArrayDeque<>();
        visited.add(start);
        queue.add(start);
        while (!queue.isEmpty()) {
            Position current = queue.poll();
            group.add(current);
            for (int[] offset : NEIGHBORS) {
                Position next = current.translate(offset[0], offset[1]);
                if (next.x() < 0 || next.x() >= context.width() || next.y() < 0 || next.y() >= context.height()) {
                    continue;
                }
                if (visited.add(next) && context.cellAt(next).occupiedBy() == type) {
                    queue.add(next);
                }
            }
        }
        return group;
    }
}
