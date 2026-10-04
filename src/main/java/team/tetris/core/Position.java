package team.tetris.core;

/**
 * 보드 위의 한 칸을 가리키는 불변 좌표.
 *
 * <p>x는 왼쪽에서 오른쪽으로, y는 위에서 아래로 증가한다.
 */
public record Position(int x, int y) {

    /**
     * 이 좌표를 (dx, dy)만큼 이동한 새 좌표를 반환한다.
     */
    public Position translate(int dx, int dy) {
        return new Position(x + dx, y + dy);
    }

    /**
     * pivot을 중심으로 이 좌표를 시계방향으로 90도 회전한 새 좌표를 반환한다.
     * 여러 번 호출하면 그만큼 90도씩 누적 회전한다.
     */
    public Position rotateAround(Position pivot) {
        int relativeX = x - pivot.x();
        int relativeY = y - pivot.y();
        return new Position(pivot.x() - relativeY, pivot.y() + relativeX);
    }
}
