package team.tetris.ui.render;

import java.util.List;
import team.tetris.core.AttackBlock;
import team.tetris.core.Cell;
import team.tetris.core.rule.AttackQueue;
import team.tetris.ui.render.palette.ColorPalette;

final class BattleInfoPanel {
    static final int INFO_OFFSET = 23;
    private static final int INFO_WIDTH = 14;
    static final int NEXT_TITLE_ROW = 2;
    private static final int SCORE_ROW = NEXT_TITLE_ROW + BoardPainter.NEXT_BOX_HEIGHT + 1;
    private static final int PENDING_TITLE_ROW = SCORE_ROW + 2;
    /** 받을 줄 상자: 안쪽 폭은 보드 칸 수(한 칸 = 한 글자), 안쪽 높이는 대기열 최대 줄 수. */
    private static final int PENDING_BOX_TOP = PENDING_TITLE_ROW + 1;
    static final String PENDING_CELL = "█";
    static final String PENDING_EMPTY = "·";

    private final ColorPalette palette;

    BattleInfoPanel(ColorPalette palette) {
        this.palette = palette;
    }

    void draw(TextFrame frame, int left, String label, int level, long score, int clearedLines,
              int doubleScoreClears, int width, List<List<Cell>> pending) {
        int info = left + INFO_OFFSET;
        frame.put(info, BattleRenderer.BOARD_TOP, label, palette.accentStyle().asBold());
        frame.putRight(info + INFO_WIDTH, BattleRenderer.BOARD_TOP, "LV " + level, palette.dimStyle());
        if (doubleScoreClears > 0) {
            // 점수 2배 아이템($)이 남은 줄 삭제 횟수. 점수 줄에 두면 네 자리 이상 점수를 덮으므로
            // 비어 있는 NEXT 제목 줄 오른쪽에 둔다.
            frame.putRight(info + INFO_WIDTH, NEXT_TITLE_ROW, "x2(" + doubleScoreClears + ")",
                    palette.accentStyle());
        }
        frame.put(info, SCORE_ROW, "SCORE", palette.dimStyle());
        frame.putRight(info + INFO_WIDTH, SCORE_ROW, Long.toString(score), palette.base().asBold());
        frame.put(info, SCORE_ROW + 1, "LINES", palette.dimStyle());
        frame.putRight(info + INFO_WIDTH, SCORE_ROW + 1, Integer.toString(clearedLines), palette.base().asBold());
        drawPending(frame, info, width, pending);
    }

    /** 받을 줄: 위에서 아래 순서의 대기열을 상자 아래쪽에 맞춰 그린다. 비어 있는 위쪽은 빈 줄이다. */
    private void drawPending(TextFrame frame, int col, int width, List<List<Cell>> pending) {
        TextStyle attack = palette.blockStyle(AttackBlock.INSTANCE);
        frame.put(col, PENDING_TITLE_ROW, "받을 줄", palette.dimStyle());
        frame.putRight(col + INFO_WIDTH, PENDING_TITLE_ROW, pending.size() + "/" + AttackQueue.MAX_LINES,
                pending.isEmpty() ? palette.dimStyle() : palette.warningStyle());
        frame.box(col, PENDING_BOX_TOP, width + 2, AttackQueue.MAX_LINES + 2, palette.borderStyle(), palette.base());
        int firstRow = PENDING_BOX_TOP + 1 + AttackQueue.MAX_LINES - pending.size();
        for (int i = 0; i < pending.size(); i++) {
            List<Cell> line = pending.get(i);
            for (int x = 0; x < line.size() && x < width; x++) {
                boolean empty = line.get(x).isEmpty();
                frame.put(col + 1 + x, firstRow + i, empty ? PENDING_EMPTY : PENDING_CELL,
                        empty ? palette.dimStyle() : attack);
            }
        }
    }

}
