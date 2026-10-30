package team.tetris.ui.render;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import team.tetris.application.GameSnapshot;
import team.tetris.application.GameStatus;
import team.tetris.application.debug.DebugSetup;
import team.tetris.application.model.Difficulty;
import team.tetris.application.model.GameMode;
import team.tetris.application.model.Settings;
import team.tetris.bootstrap.AppComposition;
import team.tetris.core.*;
import team.tetris.core.item.ChainClearItem;
import team.tetris.core.item.BombItem;
import team.tetris.core.item.SoloBlock;
import team.tetris.core.item.WeightBlock;
import team.tetris.ui.ScreenRouter;
import team.tetris.ui.input.InputMapper;
import team.tetris.ui.render.palette.BlockGlyphs;
import team.tetris.ui.render.palette.ColorPalette;

class GhostRendererTest {
    @TempDir Path directory;

    @Test void gameScreenEnablesGhostOnlyForDebugSessions() {
        for (boolean debug : new boolean[]{false, true}) {
            var app = new AppComposition(directory, debug);
            var router = new ScreenRouter(app, app.settings().loadOrDefault(), () -> {}, ignored -> {});
            for (GameMode mode : GameMode.values()) {
                if (mode == GameMode.NORMAL) router.startGame();
                else router.startItemGame();
                assertEquals(debug, router.render().text().contains(BlockGlyphs.GHOST));
            }
        }
    }

    @Test void bothPalettesShowBlockColoredGhostWithoutCopyingItemSymbols() {
        ActivePiece active = new ActivePiece(Piece.of(TetrominoType.T).withItem(0, new ChainClearItem()),
                0, new Position(3, 0));
        GameSnapshot snapshot = snapshot(emptyBoard(), active, EnginePhase.RUNNING);
        for (boolean colorBlind : new boolean[]{false, true}) {
            ColorPalette palette = ColorPalette.of(colorBlind);
            TextFrame frame = new TextRenderer(palette, new InputMapper(Settings.defaults()), true).render(snapshot);
            for (Position cell : active.movedTo(new Position(3, 18)).cells()) {
                assertEquals(BlockGlyphs.GHOST, glyphAt(frame, cell));
                assertEquals(palette.blockStyle(active.shape()),
                        frame.styleAt(TextRenderer.columnOf(cell.x()), TextRenderer.rowOf(cell.y())));
            }
            assertEquals("C ", glyphAt(frame, active.cells()[0]));
            assertEquals(palette.itemStyle(TetrominoType.T), frame.styleAt(TextRenderer.columnOf(4), TextRenderer.rowOf(0)));
        }
        assertFalse(new TextRenderer(ColorPalette.standard(), new InputMapper(Settings.defaults()))
                .render(snapshot).text().contains(BlockGlyphs.GHOST));
    }

    @Test void activeCellsTakePriorityWhereTheyOverlapTheGhost() {
        ActivePiece active = new ActivePiece(TetrominoType.O, 0, new Position(3, 17));
        TextFrame frame = render(emptyBoard(), active, EnginePhase.RUNNING);
        for (Position cell : active.cells()) assertEquals(BlockGlyphs.of(TetrominoType.O), glyphAt(frame, cell));
        assertEquals(BlockGlyphs.GHOST, glyphAt(frame, new Position(4, 19)));
        assertFalse(render(emptyBoard(), active.movedTo(new Position(3, 18)), EnginePhase.RUNNING)
                .text().contains(BlockGlyphs.GHOST));
    }

    @Test void ghostStopsAboveObstaclesAndFollowsMovementAndRotation() {
        Cell[][] board = emptyBoard();
        board[18][4] = Cell.occupiedBy(TetrominoType.J);
        ActivePiece active = new ActivePiece(TetrominoType.I, 0, new Position(3, 0));
        TextFrame first = render(board, active, EnginePhase.RUNNING);
        assertEquals(BlockGlyphs.GHOST, glyphAt(first, new Position(4, 17)));
        assertEquals(BlockGlyphs.of(TetrominoType.J), glyphAt(first, new Position(4, 18)));

        TextFrame rotated = render(board, active.rotatedTo(1, new Position(4, 0)), EnginePhase.RUNNING);
        for (int y = 16; y < 20; y++) assertEquals(BlockGlyphs.GHOST, glyphAt(rotated, new Position(6, y)));
        assertEquals(BlockGlyphs.EMPTY, glyphAt(rotated, new Position(4, 17)));
    }

    @Test void bombGhostStopsAboveOccupiedCellsWithoutChangingTheBoard() {
        Cell[][] board = new DebugSetup(5, 1, DebugSetup.HolePattern.ALIGNED, 0, Difficulty.NORMAL, 13).board();
        Cell[][] copy = Arrays.stream(board).map(Cell[]::clone).toArray(Cell[][]::new);
        ActivePiece active = new ActivePiece(new SoloBlock(new BombItem()).toItemPiece(null, null),
                0, new Position(3, 0));
        TextFrame frame = render(board, active, EnginePhase.RUNNING);
        for (Position cell : active.movedTo(new Position(3, 14)).cells()) {
            assertEquals(BlockGlyphs.GHOST, glyphAt(frame, cell));
        }
        assertArrayEquals(copy, board);
    }

    @Test void noGhostIsDrawnAfterGameOverOrWithoutAnActivePiece() {
        ActivePiece active = new ActivePiece(TetrominoType.T, 0, new Position(3, 0));
        assertFalse(render(emptyBoard(), active, EnginePhase.GAME_OVER).text().contains(BlockGlyphs.GHOST));
        assertFalse(render(emptyBoard(), null, EnginePhase.RUNNING).text().contains(BlockGlyphs.GHOST));
    }

    @Test void removalPreviewIsSubtlePreservesGlyphsAndFollowsTheItemInBothPalettes() {
        Cell[][] board = emptyBoard();
        board[18][4] = Cell.occupiedBy(TetrominoType.J);
        board[19][4] = Cell.occupiedBy(TetrominoType.Z);
        var active = new ActivePiece(new SoloBlock(new BombItem()).toItemPiece(null, null),
                0, new Position(4, 0));
        Position target = new Position(4, 18);
        int col = TextRenderer.columnOf(4), row = TextRenderer.rowOf(18);
        for (boolean colorBlind : new boolean[]{false, true}) {
            var palette = ColorPalette.of(colorBlind);
            var debug = new TextRenderer(palette, new InputMapper(Settings.defaults()), true);
            for (EnginePhase phase : new EnginePhase[]{EnginePhase.RUNNING, EnginePhase.PAUSED}) {
                var frame = debug.render(snapshot(board, active, phase));
                assertEquals(BlockGlyphs.of(TetrominoType.J), glyphAt(frame, target));
                assertEquals(palette.removalPreviewStyle(TetrominoType.J), frame.styleAt(col, row));
                assertEquals(frame.styleAt(col, row), frame.styleAt(col + 1, row));
                var bg = palette.background();
                assertEquals(new java.awt.Color((bg.getRed() * 3 + 255) / 4, (bg.getGreen() * 3 + 255) / 4,
                        (bg.getBlue() * 3 + 255) / 4), frame.styleAt(col, row).background());
                assertEquals(palette.blockStyle(TetrominoType.Z), frame.styleAt(col, row + 1));
            }
            // 지금 위치에 이미 착지했어도 고정 전에 삭제 범위를 보여준다.
            var landed = debug.render(snapshot(board, active.movedTo(new Position(4, 17)), EnginePhase.RUNNING));
            assertEquals(palette.removalPreviewStyle(TetrominoType.J), landed.styleAt(col, row));
            var moved = debug.render(snapshot(board, active.movedTo(new Position(7, 0)), EnginePhase.RUNNING));
            assertEquals(palette.blockStyle(TetrominoType.J), moved.styleAt(col, row));
            var over = debug.render(snapshot(board, active, EnginePhase.GAME_OVER));
            assertEquals(palette.blockStyle(TetrominoType.J), over.styleAt(col, row));
            var normal = new TextRenderer(palette, new InputMapper(Settings.defaults()))
                    .render(snapshot(board, active, EnginePhase.RUNNING));
            assertEquals(palette.blockStyle(TetrominoType.J), normal.styleAt(col, row));
        }
    }

    @Test void weightGhostDoesNotCoverHighlightedBlocksAtItsLandingPosition() {
        Cell[][] board = emptyBoard();
        board[10][4] = Cell.occupiedBy(TetrominoType.J);
        board[19][4] = Cell.occupiedBy(TetrominoType.Z);
        var active = new ActivePiece(new WeightBlock().toItemPiece(null, null), 0, new Position(3, 0));
        var frame = render(board, active, EnginePhase.RUNNING);
        var palette = ColorPalette.standard();
        for (int y : new int[]{10, 19}) {
            var type = board[y][4].occupiedBy();
            assertEquals(BlockGlyphs.of(type), glyphAt(frame, new Position(4, y)));
            assertEquals(palette.removalPreviewStyle(type),
                    frame.styleAt(TextRenderer.columnOf(4), TextRenderer.rowOf(y)));
        }
        assertEquals(BlockGlyphs.GHOST, glyphAt(frame, new Position(3, 19)));
    }

    private static GameSnapshot snapshot(Cell[][] board, ActivePiece active, EnginePhase phase) {
        return new GameSnapshot(new EngineSnapshot(board, active, Piece.of(TetrominoType.O), phase),
                0, 0, 0, 1_000_000_000, phase == EnginePhase.GAME_OVER ? GameStatus.GAME_OVER : GameStatus.RUNNING);
    }

    private static TextFrame render(Cell[][] board, ActivePiece active, EnginePhase phase) {
        return new TextRenderer(ColorPalette.standard(), new InputMapper(Settings.defaults()), true)
                .render(snapshot(board, active, phase));
    }

    private static String glyphAt(TextFrame frame, Position cell) {
        int column = TextRenderer.columnOf(cell.x());
        int row = TextRenderer.rowOf(cell.y());
        StringBuilder glyph = new StringBuilder();
        for (int i = 0; i < TextRenderer.CELL_WIDTH; i++) {
            if (!frame.isContinuation(column + i, row)) glyph.appendCodePoint(frame.codePointAt(column + i, row));
        }
        return glyph.toString();
    }

    private static Cell[][] emptyBoard() {
        Cell[][] board = new Cell[20][10];
        for (Cell[] row : board) Arrays.fill(row, Cell.EMPTY);
        return board;
    }
}
