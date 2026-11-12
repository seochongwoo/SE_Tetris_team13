package team.tetris.core.rule;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import team.tetris.core.ActivePiece;
import team.tetris.core.AttackBlock;
import team.tetris.core.Cell;
import team.tetris.core.Position;
import team.tetris.core.result.ClearResult;
import team.tetris.core.result.ClearedRow;
import team.tetris.core.result.LockResult;

/**
 * 블록 고정 한 번의 결과로 상대에게 보낼 공격 줄을 계산한다 (3차 R3-06).
 *
 * <ul>
 *   <li>줄 단위로 지운 줄(꽉 찬 줄, 줄 삭제 아이템 {@code L})만 센다. 폭탄·연쇄 삭제처럼 칸 단위로 지운
 *       칸은 {@link ClearResult}에 들어가지 않으므로 공격이 되지 않는다 (안건 ⑭).</li>
 *   <li>한 번에 2줄 이상 지웠을 때만 공격한다.</li>
 *   <li>지운 줄에서 마지막으로 고정한 블록의 칸을 비운 모양을 보낸다. 무게추도 최종 고정 칸을 비운다
 *       (안건 ⑮).</li>
 *   <li>남은 칸은 아이템을 지우고 회색 공격 칸({@link AttackBlock})으로 바꾼다 (안건 ㉔).</li>
 *   <li>지운 줄의 위아래 순서를 유지한다.</li>
 * </ul>
 */
public final class AttackRule {

    /** 공격이 되는 최소 삭제 줄 수. */
    public static final int MIN_CLEARED_LINES = 2;

    private static final Cell ATTACK = Cell.occupiedBy(AttackBlock.INSTANCE);

    private AttackRule() {
    }

    /**
     * 고정 결과로 보낼 공격 줄을 위에서 아래 순서로 반환한다. 공격이 아니면 빈 목록.
     *
     * @param lock  이번에 고정된 블록 (고정이 없었으면 null)
     * @param clear 이번 고정으로 지운 줄 (없으면 null)
     */
    public static List<List<Cell>> linesFor(LockResult lock, ClearResult clear) {
        if (lock == null || clear == null || clear.lineCount() < MIN_CLEARED_LINES) {
            return List.of();
        }
        Set<Position> lastPiece = new HashSet<>(
                List.of(new ActivePiece(lock.shape(), lock.rotation(), lock.origin()).cells()));
        List<List<Cell>> lines = new ArrayList<>();
        for (ClearedRow row : clear.clearedRows()) {
            List<Cell> line = new ArrayList<>(row.cells().size());
            boolean hasGap = false;
            for (int x = 0; x < row.cells().size(); x++) {
                Cell cell = row.cells().get(x);
                boolean empty = cell.isEmpty() || lastPiece.contains(new Position(x, row.rowIndex()));
                line.add(empty ? Cell.EMPTY : ATTACK);
                hasGap |= empty;
            }
            // 정상 진행에서는 지운 줄에 항상 마지막 블록 칸이 있다. 꽉 찬 채로 시작한 보드처럼 빈칸이 없는
            // 줄은 받은 쪽에서 저절로 지워지므로 보내지 않는다.
            if (hasGap) {
                lines.add(List.copyOf(line));
            }
        }
        return List.copyOf(lines);
    }
}
