package team.tetris.core.rule;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import team.tetris.core.Cell;

/**
 * 받은 공격 줄이 상대 보드에 들어가기 전까지 기다리는 대기열 (3차 R3-14~17). 위에서 아래 순서로
 * 보관하며, 받는 쪽 엔진에는 {@link IncomingLines}로 연결해 블록이 고정될 때 한꺼번에 꺼내 간다.
 *
 * <ul>
 *   <li>최대 {@value #MAX_LINES}줄 (R3-14)</li>
 *   <li>새 공격은 이미 있는 줄의 아래에 붙인다 (R3-15)</li>
 *   <li>이미 {@value #MAX_LINES}줄이면 새 공격은 무시한다 (R3-16)</li>
 *   <li>붙여서 {@value #MAX_LINES}줄을 넘으면 제일 아래쪽을 잘라낸다 (R3-17)</li>
 * </ul>
 *
 * <p>줄은 넣을 때 검사한다. 받는 쪽 보드 폭과 다르거나 빈칸이 없는 줄은 블록 고정 도중에야
 * 드러나지 않도록 {@link #add}에서 바로 거부하고, 그 공격은 하나도 넣지 않는다.
 *
 * <p>대전에서는 공격을 보내는 쪽과 꺼내 가는 쪽이 다른 스레드일 수 있으므로(P2P 수신) 모든 메서드를
 * 동기화한다.
 */
public final class AttackQueue implements IncomingLines {

    /** 대기열에 둘 수 있는 최대 줄 수. */
    public static final int MAX_LINES = 10;

    private final int width;
    private final List<List<Cell>> lines = new ArrayList<>();

    /** @param width 이 대기열의 줄을 받을 보드의 폭 */
    public AttackQueue(int width) {
        if (width <= 0) {
            throw new IllegalArgumentException("width must be positive: " + width);
        }
        this.width = width;
    }

    /**
     * 공격 줄을 대기열 아래에 붙인다. 이미 가득 차 있으면 무시하고, 넘치는 부분은 아래쪽부터 버린다.
     *
     * @param attack 위에서 아래 순서의 공격 줄
     * @return 실제로 대기열에 들어간 줄 수
     * @throws IllegalArgumentException 폭이 다르거나 빈칸이 없는 줄이 있을 때 (아무 줄도 넣지 않음)
     */
    public synchronized int add(List<List<Cell>> attack) {
        Objects.requireNonNull(attack, "attack");
        for (List<Cell> line : attack) {
            validate(line);
        }
        int accepted = Math.min(attack.size(), MAX_LINES - lines.size());
        for (List<Cell> line : attack.subList(0, accepted)) {
            lines.add(List.copyOf(line));
        }
        return accepted;
    }

    /** 기다리던 줄을 모두 꺼내고 대기열을 비운다. */
    @Override
    public synchronized List<List<Cell>> take() {
        List<List<Cell>> taken = List.copyOf(lines);
        lines.clear();
        return taken;
    }

    /** 지금 기다리는 줄 (화면의 대기 줄 영역 표시용). 대기열은 바뀌지 않는다. */
    public synchronized List<List<Cell>> pending() {
        return List.copyOf(lines);
    }

    private void validate(List<Cell> line) {
        Objects.requireNonNull(line, "line");
        if (line.size() != width) {
            throw new IllegalArgumentException("Line must have " + width + " cells: " + line.size());
        }
        boolean hasGap = false;
        for (Cell cell : line) {
            hasGap |= Objects.requireNonNull(cell, "cell").isEmpty();
        }
        if (!hasGap) {
            throw new IllegalArgumentException("Line must have at least one empty cell");
        }
    }

    /** 지금 기다리는 줄 수. */
    public synchronized int size() {
        return lines.size();
    }
}
