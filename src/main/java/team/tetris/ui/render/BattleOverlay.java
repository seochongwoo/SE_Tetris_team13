package team.tetris.ui.render;

import java.util.List;
import team.tetris.ui.render.palette.ColorPalette;

/**
 * 대전 화면 위에 겹쳐 그리는 결과 표시와 대화상자. 로컬 대전과 P2P 경기가 같은 모양을 쓰도록 한곳에 둔다.
 */
public final class BattleOverlay {

    static final int DIALOG_WIDTH = 32;

    private BattleOverlay() {
    }

    /**
     * 보드 위쪽에 WIN / LOSE / DRAW를 표시한다. 블록 타일(두 칸) 경계에 맞춰야 반쪽 남은 타일이 글자로
     * 보이지 않으므로 보드 칸 2~7을 덮는다.
     */
    public static void outcomeTag(TextFrame frame, ColorPalette palette, int boardLeft, String tag, boolean win) {
        int left = boardLeft + 1 + 2 * 2;
        frame.box(left, 3, 12, 3, palette.borderStyle(), palette.base());
        frame.putCentered(left, left + 12, 4, tag, win ? palette.accentStyle() : palette.warningStyle());
    }

    /**
     * 화면 가운데 대화상자. 제목, 한 줄 설명(없으면 null), 고를 항목(없으면 빈 목록), 맨 아래 안내를 그린다.
     * cursor는 강조할 항목 번호다.
     */
    public static void dialog(TextFrame frame, ColorPalette palette, String title, String subtitle,
                              List<String> items, int cursor, String hint) {
        int extra = subtitle == null ? 0 : 1;
        int height = items.size() + 6 + extra;
        // 기본 폭은 32칸. 오류 사유처럼 긴 글은 들어가도록 넓히되 화면 밖으로는 나가지 않는다.
        int longest = Math.max(TextFrame.displayWidth(title), TextFrame.displayWidth(hint));
        if (subtitle != null) {
            longest = Math.max(longest, TextFrame.displayWidth(subtitle));
        }
        int width = Math.min(frame.columns() - 2, Math.max(DIALOG_WIDTH, longest + 4));
        int col = (frame.columns() - width) / 2;
        int top = (frame.rows() - height) / 2;
        frame.box(col, top, width, height, palette.borderStyle(), palette.base());
        frame.putCentered(col, col + width, top + 1, title, palette.accentStyle());
        if (subtitle != null) {
            frame.putCentered(col, col + width, top + 2, subtitle, palette.base());
        }
        for (int i = 0; i < items.size(); i++) {
            int row = top + 3 + extra + i;
            if (i == cursor) {
                frame.fill(col + 1, row, width - 2, 1, palette.highlightStyle());
                frame.putCentered(col, col + width, row, "► " + items.get(i), palette.highlightStyle());
            } else {
                frame.putCentered(col, col + width, row, items.get(i), palette.base());
            }
        }
        frame.putCentered(col, col + width, top + height - 2, hint, palette.dimStyle());
    }
}
