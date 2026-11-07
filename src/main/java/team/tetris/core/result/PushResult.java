package team.tetris.core.result;

import java.util.List;
import team.tetris.core.Cell;

/**
 * 보드 아래에 줄을 넣은 결과(대전의 공격 줄). 넣은 줄 수만큼 보드 위로 밀려 나간 줄을 위에서 아래
 * 순서로 담는다. 기존 보드의 맨 위 줄부터 들어가고, 보드 높이보다 많이 넣었다면 넣은 줄 중 보드에
 * 남지 못한 줄이 그 뒤에 온다. 삭제 연출은 이 줄로 공격 줄이 들어오기 전 보드를 복원한다.
 *
 * @param pushedOutRows 보드 위로 밀려 나간 줄 (넣은 줄 수와 같은 개수)
 */
public record PushResult(List<List<Cell>> pushedOutRows) {

    /** 줄을 넣지 않은 결과. */
    public static final PushResult NONE = new PushResult(List.of());

    public PushResult {
        pushedOutRows = pushedOutRows.stream().map(List::copyOf).toList();
    }

    /** 넣은 줄 수. */
    public int addedLines() {
        return pushedOutRows.size();
    }

    /** 밀려 나간 줄에 블록 칸이 하나라도 있으면 true (패배 판정은 호출한 쪽이 정한다). */
    public boolean overflowed() {
        return pushedOutRows.stream().flatMap(List::stream).anyMatch(cell -> !cell.isEmpty());
    }
}
