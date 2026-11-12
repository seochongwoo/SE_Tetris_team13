package team.tetris.ui.render;

import java.util.List;
import team.tetris.application.GameCommand;
import team.tetris.application.GameSnapshot;
import team.tetris.application.model.GameMode;
import team.tetris.ui.DisplayNames;
import team.tetris.ui.input.InputMapper;
import team.tetris.ui.input.KeyNames;
import team.tetris.ui.render.palette.ColorPalette;

/**
 * 1인 게임 화면을 텍스트 격자로 그린다. 왼쪽에 테두리를 두른 보드(블록 한 칸 = 두 글자),
 * 오른쪽에 모드·난이도, 다음 블록, 점수·레벨·지운 줄 수, 조작 키 안내를 둔다. 보드와 다음 블록 상자는
 * {@link BoardPainter}가 그린다.
 */
public final class TextRenderer implements Renderer {

    /** 보드 테두리의 왼쪽 열과 위쪽 행. */
    public static final int BOARD_LEFT = 1;
    public static final int BOARD_TOP = 1;
    /** 블록 한 칸이 차지하는 글자 수. */
    public static final int CELL_WIDTH = BoardPainter.CELL_WIDTH;
    /** 오른쪽 정보 영역이 시작하는 열. */
    public static final int PANEL_LEFT = 25;
    private static final int MODE_ROW = BOARD_TOP;
    static final int NEXT_TITLE_ROW = MODE_ROW + 2;

    private static final int STATS_TOP = NEXT_TITLE_ROW + BoardPainter.NEXT_BOX_HEIGHT + 2;
    private static final int HELP_TOP = STATS_TOP + 6;

    private record Help(String label, List<GameCommand> commands) {
    }

    private static final List<Help> HELP = List.of(
            new Help("이동", List.of(GameCommand.MOVE_LEFT, GameCommand.MOVE_RIGHT)),
            new Help("회전", List.of(GameCommand.ROTATE_CW)),
            new Help("내리기", List.of(GameCommand.SOFT_DROP)),
            new Help("떨어뜨리기", List.of(GameCommand.HARD_DROP)),
            new Help("일시정지", List.of(GameCommand.PAUSE)),
            new Help("메뉴", List.of(GameCommand.QUIT_GAME)));

    private final ColorPalette palette;
    private final InputMapper keys;
    private final boolean showLanding;
    private final BoardPainter board;

    public TextRenderer(ColorPalette palette, InputMapper keys) {
        this(palette, keys, false);
    }

    /** showLanding은 디버그 화면에서만 켜는 착지 미리보기다. */
    public TextRenderer(ColorPalette palette, InputMapper keys, boolean showLanding) {
        this.palette = palette;
        this.keys = keys;
        this.showLanding = showLanding;
        this.board = new BoardPainter(palette, BOARD_LEFT, BOARD_TOP);
    }

    /** 보드 칸 x가 화면에서 시작하는 열. */
    public static int columnOf(int x) {
        return BOARD_LEFT + 1 + x * CELL_WIDTH;
    }

    /** 보드 칸 y가 화면에서 놓이는 행. */
    public static int rowOf(int y) {
        return BOARD_TOP + 1 + y;
    }

    @Override
    public TextFrame render(GameSnapshot snapshot) {
        TextFrame frame = TextFrame.blank(palette.base());
        board.drawPlayfield(frame, snapshot, showLanding);
        board.drawNext(frame, snapshot.engine().nextPiece(), PANEL_LEFT, NEXT_TITLE_ROW);
        drawStats(frame, snapshot);
        drawHelp(frame);
        return frame;
    }

    private void drawStats(TextFrame frame, GameSnapshot snapshot) {
        int row = STATS_TOP;
        String mode = snapshot.mode() == GameMode.ITEM ? "아이템" : "일반";
        frame.put(PANEL_LEFT, MODE_ROW, mode + " / " + DisplayNames.of(snapshot.difficulty()), palette.accentStyle());
        drawStat(frame, row, "SCORE", Long.toString(snapshot.score()));
        if (snapshot.doubleScoreClears() > 0) {
            // 점수 2배 아이템($)이 남은 줄 삭제 횟수
            frame.put(PANEL_LEFT + 6, row, "x2 (" + snapshot.doubleScoreClears() + ")", palette.accentStyle());
        }
        drawStat(frame, row + 2, "LEVEL", Integer.toString(snapshot.level()));
        drawStat(frame, row + 4, "LINES", Integer.toString(snapshot.clearedLines()));
    }

    private void drawStat(TextFrame frame, int row, String label, String value) {
        frame.put(PANEL_LEFT, row, label, palette.dimStyle());
        frame.put(PANEL_LEFT, row + 1, value, palette.base().asBold());
    }

    private void drawHelp(TextFrame frame) {
        int row = HELP_TOP;
        for (Help help : HELP) {
            StringBuilder keyText = new StringBuilder();
            for (GameCommand command : help.commands()) {
                if (!keyText.isEmpty()) {
                    keyText.append(' ');
                }
                keyText.append(KeyNames.display(keys.keyFor(command)));
            }
            int col = frame.put(PANEL_LEFT, row, keyText.toString(), palette.accentStyle());
            frame.put(col + 1, row, help.label(), palette.dimStyle());
            row++;
        }
    }
}
