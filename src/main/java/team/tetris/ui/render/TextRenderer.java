package team.tetris.ui.render;

import java.util.List;
import team.tetris.application.GameCommand;
import team.tetris.application.GameSnapshot;
import team.tetris.core.ActivePiece;
import team.tetris.core.Cell;
import team.tetris.core.EngineSnapshot;
import team.tetris.core.Piece;
import team.tetris.core.Position;
import team.tetris.core.Shape;
import team.tetris.core.item.Item;
import team.tetris.ui.input.InputMapper;
import team.tetris.ui.input.KeyNames;
import team.tetris.ui.render.palette.BlockGlyphs;
import team.tetris.ui.render.palette.ColorPalette;

/**
 * 게임 화면을 텍스트 격자로 그린다. 왼쪽에 테두리를 두른 보드(블록 한 칸 = 두 글자),
 * 오른쪽에 다음 블록, 점수·레벨·지운 줄 수, 조작 키 안내를 둔다.
 *
 * <p>보드에는 고정된 칸만 들어 있으므로, 낙하 중인 블록은 각 칸의 보드 좌표를 구해 따로 겹쳐
 * 그린다. 아이템이 실린 칸은 아이템 문자를 반전 색으로 그려 일반 무늬와 구분한다.
 */
public final class TextRenderer implements Renderer {

    /** 보드 테두리의 왼쪽 열과 위쪽 행. */
    public static final int BOARD_LEFT = 1;
    public static final int BOARD_TOP = 1;
    /** 블록 한 칸이 차지하는 글자 수. */
    public static final int CELL_WIDTH = 2;
    /** 오른쪽 정보 영역이 시작하는 열. */
    public static final int PANEL_LEFT = 25;

    private static final int NEXT_BOX_WIDTH = 10;
    private static final int NEXT_BOX_HEIGHT = 6;

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

    public TextRenderer(ColorPalette palette, InputMapper keys) {
        this.palette = palette;
        this.keys = keys;
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
        EngineSnapshot engine = snapshot.engine();
        Cell[][] board = engine.board();
        drawBoard(frame, board);
        drawActivePiece(frame, engine.activePiece(), board[0].length, board.length);
        drawNext(frame, engine.nextPiece());
        drawStats(frame, snapshot);
        drawHelp(frame);
        return frame;
    }

    private void drawBoard(TextFrame frame, Cell[][] board) {
        int height = board.length;
        int width = board[0].length;
        frame.box(BOARD_LEFT, BOARD_TOP, width * CELL_WIDTH + 2, height + 2, palette.borderStyle(), palette.base());
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                Cell cell = board[y][x];
                if (cell.isEmpty()) {
                    frame.put(columnOf(x), rowOf(y), BlockGlyphs.EMPTY, palette.dimStyle());
                } else {
                    drawCell(frame, columnOf(x), rowOf(y), cell.occupiedBy(), cell.item());
                }
            }
        }
    }

    private void drawActivePiece(TextFrame frame, ActivePiece piece, int width, int height) {
        if (piece == null) {
            return;
        }
        Position[] cells = piece.cells();
        for (int i = 0; i < cells.length; i++) {
            int x = cells[i].x();
            int y = cells[i].y();
            if (x >= 0 && x < width && y >= 0 && y < height) {
                drawCell(frame, columnOf(x), rowOf(y), piece.shape(), piece.itemAt(i));
            }
        }
    }

    private void drawNext(TextFrame frame, Piece next) {
        frame.put(PANEL_LEFT, BOARD_TOP, "NEXT", palette.accentStyle());
        int boxTop = BOARD_TOP + 1;
        frame.box(PANEL_LEFT, boxTop, NEXT_BOX_WIDTH, NEXT_BOX_HEIGHT, palette.borderStyle(), palette.base());
        if (next == null) {
            return;
        }
        Position[] cells = next.shape().cellsAt(0);
        int minX = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int minY = Integer.MAX_VALUE;
        int maxY = Integer.MIN_VALUE;
        for (Position cell : cells) {
            minX = Math.min(minX, cell.x());
            maxX = Math.max(maxX, cell.x());
            minY = Math.min(minY, cell.y());
            maxY = Math.max(maxY, cell.y());
        }
        int innerWidth = NEXT_BOX_WIDTH - 2;
        int innerHeight = NEXT_BOX_HEIGHT - 2;
        int startCol = PANEL_LEFT + 1 + (innerWidth - (maxX - minX + 1) * CELL_WIDTH) / 2;
        int startRow = boxTop + 1 + (innerHeight - (maxY - minY + 1)) / 2;
        for (int i = 0; i < cells.length; i++) {
            drawCell(frame, startCol + (cells[i].x() - minX) * CELL_WIDTH, startRow + (cells[i].y() - minY),
                    next.shape(), next.itemAt(i));
        }
    }

    private void drawStats(TextFrame frame, GameSnapshot snapshot) {
        int row = BOARD_TOP + NEXT_BOX_HEIGHT + 2;
        drawStat(frame, row, "SCORE", Long.toString(snapshot.score()));
        drawStat(frame, row + 2, "LEVEL", Integer.toString(snapshot.level()));
        drawStat(frame, row + 4, "LINES", Integer.toString(snapshot.clearedLines()));
    }

    private void drawStat(TextFrame frame, int row, String label, String value) {
        frame.put(PANEL_LEFT, row, label, palette.dimStyle());
        frame.put(PANEL_LEFT, row + 1, value, palette.base().asBold());
    }

    private void drawHelp(TextFrame frame) {
        int row = BOARD_TOP + NEXT_BOX_HEIGHT + 9;
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

    private void drawCell(TextFrame frame, int col, int row, Shape shape, Item item) {
        if (item != null) {
            frame.put(col, row, BlockGlyphs.of(item), palette.itemStyle(shape));
        } else {
            frame.put(col, row, BlockGlyphs.of(shape), palette.blockStyle(shape));
        }
    }
}
