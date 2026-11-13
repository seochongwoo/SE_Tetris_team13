package team.tetris.ui.render;

import java.util.BitSet;
import java.util.Map;
import java.util.TreeMap;
import team.tetris.application.GameSnapshot;
import team.tetris.application.LineClearAnimation;
import team.tetris.core.ActivePiece;
import team.tetris.core.Cell;
import team.tetris.core.EnginePhase;
import team.tetris.core.EngineSnapshot;
import team.tetris.core.Piece;
import team.tetris.core.Position;
import team.tetris.core.Shape;
import team.tetris.core.item.Item;
import team.tetris.core.item.WeightBlock;
import team.tetris.core.rule.HardDropPreview;
import team.tetris.ui.render.palette.BlockGlyphs;
import team.tetris.ui.render.palette.ColorPalette;

/**
 * 테두리를 두른 보드 하나와 다음 블록 상자를 격자의 정해진 위치에 그린다. 1인 게임 화면은 왼쪽에 하나,
 * 대전 화면은 플레이어마다 하나씩 쓴다. 블록 한 칸은 두 글자다.
 *
 * <p>보드에는 고정된 칸만 들어 있으므로, 낙하 중인 블록은 각 칸의 보드 좌표를 구해 따로 겹쳐 그린다.
 * 아이템이 실린 칸은 아이템 문자를 반전 색으로 그려 일반 무늬와 구분한다. 줄 삭제 연출 중에는 삭제
 * 직전 보드를 보여 주고 지워지는 줄만 점멸한 뒤 가운데부터 지운다.
 */
public final class BoardPainter {

    /** 블록 한 칸이 차지하는 글자 수. */
    public static final int CELL_WIDTH = 2;
    static final int NEXT_BOX_WIDTH = 10;
    static final int NEXT_BOX_HEIGHT = 6;

    private final ColorPalette palette;
    private final int left;
    private final int top;

    /** @param left 보드 테두리의 왼쪽 열, top 위쪽 행 */
    public BoardPainter(ColorPalette palette, int left, int top) {
        this.palette = palette;
        this.left = left;
        this.top = top;
    }

    /** 보드 칸 x가 화면에서 시작하는 열. */
    public int columnOf(int x) {
        return left + 1 + x * CELL_WIDTH;
    }

    /** 보드 칸 y가 화면에서 놓이는 행. */
    public int rowOf(int y) {
        return top + 1 + y;
    }

    /** 테두리를 포함한 보드의 글자 폭. */
    public static int outerWidth(int boardWidth) {
        return boardWidth * CELL_WIDTH + 2;
    }

    /**
     * 보드와 낙하 중인 블록을 그린다. 줄 삭제 연출 중이면 연출 상태를, showLanding이면 착지 미리보기를
     * 함께 그린다.
     */
    public void drawPlayfield(TextFrame frame, GameSnapshot snapshot, boolean showLanding) {
        EngineSnapshot engine = snapshot.engine();
        Cell[][] board = engine.board();
        var animation = snapshot.lineClearAnimation();
        if (animation != null) board = animation.boardBeforeClear(board);
        drawBoard(frame, board);
        if (animation != null) {
            drawActivePiece(frame, animation.activePiece(), board[0].length, board.length);
            drawClearingRows(frame, animation, board[0].length);
        } else {
            if (showLanding && (engine.phase() == EnginePhase.RUNNING || engine.phase() == EnginePhase.PAUSED)) {
                var preview = HardDropPreview.project(board, engine.activePiece());
                drawRemovalPreview(frame, board, preview);
                drawLanding(frame, board, engine.activePiece(), preview);
            }
            drawActivePiece(frame, engine.activePiece(), board[0].length, board.length);
        }
    }

    /** 삭제 대상만 점멸하고, 각 행의 삭제 범위 중앙부터 지운다. 대상 밖 칸은 그대로 둔다. */
    private void drawClearingRows(TextFrame frame, LineClearAnimation animation, int width) {
        Map<Integer, BitSet> targets = new TreeMap<>();
        for (var row : animation.rows()) {
            targets.computeIfAbsent(row.rowIndex(), ignored -> new BitSet(width)).set(0, width);
        }
        for (var removed : animation.removedCells()) {
            targets.computeIfAbsent(removed.position().y(), ignored -> new BitSet(width)).set(removed.position().x());
        }
        for (var target : targets.entrySet()) {
            int y = target.getKey();
            BitSet columns = target.getValue();
            int first = columns.nextSetBit(0);
            int last = columns.length() - 1;
            int rings = (last - first + 2) / 2;
            long wipeNanos = animation.elapsedNanos() - LineClearAnimation.FLASH_NANOS;
            int removedRings = 1 + (int) (wipeNanos * rings
                    / (LineClearAnimation.DURATION_NANOS - LineClearAnimation.FLASH_NANOS));
            for (int x = first; x >= 0; x = columns.nextSetBit(x + 1)) {
                if (animation.flashing()) {
                    frame.put(columnOf(x), rowOf(y), "  ", palette.lineClearStyle());
                } else if (Math.abs(2 * x - first - last) / 2 < removedRings) {
                    frame.putTile(columnOf(x), rowOf(y), BlockGlyphs.EMPTY, palette.dimStyle(), palette.emptyTile());
                }
            }
        }
    }

    private void drawBoard(TextFrame frame, Cell[][] board) {
        int height = board.length;
        int width = board[0].length;
        frame.box(left, top, outerWidth(width), height + 2, palette.borderStyle(), palette.base());
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                Cell cell = board[y][x];
                if (cell.isEmpty()) {
                    frame.putTile(columnOf(x), rowOf(y), BlockGlyphs.EMPTY, palette.dimStyle(), palette.emptyTile());
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

    private void drawRemovalPreview(TextFrame frame, Cell[][] board, HardDropPreview preview) {
        for (Position position : preview.removedCells()) {
            Cell cell = board[position.y()][position.x()];
            String glyph = cell.item() == null ? BlockGlyphs.of(cell.occupiedBy()) : BlockGlyphs.of(cell.item());
            frame.putTile(columnOf(position.x()), rowOf(position.y()), glyph,
                    palette.removalPreviewStyle(cell.occupiedBy()), palette.previewTile(cell.occupiedBy(), cell.item()));
        }
    }

    private void drawLanding(TextFrame frame, Cell[][] board, ActivePiece piece, HardDropPreview preview) {
        ActivePiece landing = preview.landing();
        if (landing == null || landing.origin().equals(piece.origin())) return;
        for (Position cell : landing.cells()) {
            if (cell.x() >= 0 && cell.x() < board[0].length && cell.y() >= 0 && cell.y() < board.length) {
                // 무게추 착지 위치에 있는 기존 블록의 삭제 강조를 그림자로 덮지 않는다.
                if (preview.removedCells().contains(cell)) continue;
                frame.putTile(columnOf(cell.x()), rowOf(cell.y()), BlockGlyphs.GHOST, palette.blockStyle(piece.shape()),
                        palette.ghostTile(piece.shape()));
            }
        }
    }

    /** "NEXT" 제목을 titleRow에, 그 아래에 다음 블록 상자를 col부터 그린다. */
    public void drawNext(TextFrame frame, Piece next, int col, int titleRow) {
        frame.put(col, titleRow, "NEXT", palette.accentStyle());
        int boxTop = titleRow + 1;
        frame.box(col, boxTop, NEXT_BOX_WIDTH, NEXT_BOX_HEIGHT, palette.borderStyle(), palette.base());
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
        int startCol = col + 1 + (innerWidth - (maxX - minX + 1) * CELL_WIDTH) / 2;
        int startRow = boxTop + 1 + (innerHeight - (maxY - minY + 1)) / 2;
        for (int i = 0; i < cells.length; i++) {
            drawCell(frame, startCol + (cells[i].x() - minX) * CELL_WIDTH, startRow + (cells[i].y() - minY),
                    next.shape(), next.itemAt(i));
        }
    }

    /** 블록 한 칸. 아이템 칸은 아이템 문자를, 일반 칸은 블록 무늬를 그린다. */
    public void drawCell(TextFrame frame, int col, int row, Shape shape, Item item) {
        if (item != null && !(item instanceof WeightBlock)) {
            frame.putTile(col, row, BlockGlyphs.of(item), palette.itemStyle(shape), palette.itemTile(shape, item));
        } else {
            frame.putTile(col, row, BlockGlyphs.of(shape), palette.blockStyle(shape), palette.blockTile(shape));
        }
    }
}
