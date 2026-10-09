package team.tetris.core.result;

import java.util.List;
import team.tetris.core.Cell;

/**
 * 라인 클리어로 지워진 한 행. rowIndex는 지워지기 직전, 보드 상에서의 행 번호(0 = 맨 위)다.
 * cells는 지워지기 직전 그 행의 칸들로, UI가 삭제 애니메이션을 그릴 때 쓴다.
 */
public record ClearedRow(int rowIndex, List<Cell> cells) {

    public ClearedRow {
        cells = List.copyOf(cells);
    }
}
