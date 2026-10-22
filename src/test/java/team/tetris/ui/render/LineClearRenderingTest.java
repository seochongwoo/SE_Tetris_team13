package team.tetris.ui.render;

import static org.junit.jupiter.api.Assertions.*;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import team.tetris.application.GameSnapshot;
import team.tetris.application.GameStatus;
import team.tetris.application.LineClearAnimation;
import team.tetris.application.model.Settings;
import team.tetris.core.*;
import team.tetris.core.result.RemovedCell;
import team.tetris.ui.input.InputMapper;
import team.tetris.ui.render.palette.BlockGlyphs;
import team.tetris.ui.render.palette.ColorPalette;

class LineClearRenderingTest {
    @ParameterizedTest @ValueSource(booleans = {false, true})
    void flashesWholeRowsThenHidesThemBeforeCompactionInBothPalettes(boolean colorBlind) {
        Cell[][] cells = new Board(10, 20).snapshot();
        cells[16][0] = Cell.occupiedBy(TetrominoType.J);
        cells[17][0] = Cell.occupiedBy(TetrominoType.L);
        cells[18][0] = Cell.occupiedBy(TetrominoType.Z);
        cells[19][0] = Cell.occupiedBy(TetrominoType.I);
        Board board = new Board(cells);
        var clear = board.clearRows(Set.of(17, 19));
        var engine = new EngineSnapshot(board.snapshot(),
                new ActivePiece(TetrominoType.T, 0, new Position(3, 0)),
                Piece.of(TetrominoType.O), EnginePhase.RUNNING);
        var colors = ColorPalette.of(colorBlind);
        var renderer = new TextRenderer(colors, new InputMapper(Settings.defaults()), true);
        var flashing = renderer.render(snapshot(engine, new LineClearAnimation(clear.clearedRows(), 0)));
        var hidden = renderer.render(snapshot(engine,
                new LineClearAnimation(clear.clearedRows(), LineClearAnimation.DURATION_NANOS - 1)));
        for (int y : new int[]{17, 19}) {
            for (int x = 0; x < 10 * TextRenderer.CELL_WIDTH; x++) {
                int col = TextRenderer.columnOf(0) + x;
                assertEquals(Color.WHITE, flashing.styleAt(col, TextRenderer.rowOf(y)).background());
                assertEquals(colors.background(), hidden.styleAt(col, TextRenderer.rowOf(y)).background());
            }
            for (int x = 0; x < 10; x++) assertCell(hidden, x, y, BlockGlyphs.EMPTY);
        }
        for (TextFrame frame : new TextFrame[]{flashing, hidden}) {
            assertCell(frame, 0, 16, BlockGlyphs.of(TetrominoType.J));
            assertCell(frame, 0, 18, BlockGlyphs.of(TetrominoType.Z));
            assertCell(frame, 4, 0, BlockGlyphs.EMPTY); // 다음 블록은 아직 표시하지 않는다.
            assertFalse(frame.text().contains(BlockGlyphs.GHOST));
            assertEquals('│', frame.codePointAt(TextRenderer.BOARD_LEFT, TextRenderer.rowOf(19)));
        }
        var finished = renderer.render(snapshot(engine, null));
        assertCell(finished, 0, 18, BlockGlyphs.of(TetrominoType.J));
        assertCell(finished, 0, 19, BlockGlyphs.of(TetrominoType.Z));
        assertCell(finished, 4, 0, BlockGlyphs.of(TetrominoType.T));
    }

    @ParameterizedTest
    @CsvSource({"10,false", "10,true", "5,false", "5,true"})
    void wipesRowsFromTheCenterOutwardAtEachStep(int width, boolean colorBlind) {
        Cell[][] cells = new Board(width, 20).snapshot();
        for (int y : new int[]{17, 19}) {
            for (int x = 0; x < width; x++) cells[y][x] = Cell.occupiedBy(TetrominoType.O);
        }
        Board board = new Board(cells);
        var clear = board.clearRows(Set.of(17, 19));
        var engine = new EngineSnapshot(board.snapshot(), null, Piece.of(TetrominoType.T), EnginePhase.RUNNING);
        var colors = ColorPalette.of(colorBlind);
        var renderer = new TextRenderer(colors, new InputMapper(Settings.defaults()));
        var flash = renderer.render(snapshot(engine, new LineClearAnimation(clear.clearedRows(), 59_999_999L)));
        assertEquals(Color.WHITE, flash.styleAt(TextRenderer.columnOf(0), TextRenderer.rowOf(19)).background());
        int pairs = (width + 1) / 2;
        for (int step = 0; step < pairs; step++) {
            long boundary = 60_000_000L + step * 240_000_000L / pairs;
            for (long elapsed : new long[]{boundary, boundary + 240_000_000L / pairs - 1}) {
                var frame = renderer.render(snapshot(engine, new LineClearAnimation(clear.clearedRows(), elapsed)));
                int left = (width - 1) / 2 - step;
                int right = width / 2 + step;
                for (int y : new int[]{17, 19}) {
                    for (int x = 0; x < width; x++) {
                        assertCell(frame, x, y, x >= left && x <= right
                                ? BlockGlyphs.EMPTY : BlockGlyphs.of(TetrominoType.O));
                        assertEquals(colors.background(), frame.styleAt(TextRenderer.columnOf(x), TextRenderer.rowOf(y)).background());
                    }
                }
            }
        }
    }

    private static GameSnapshot snapshot(EngineSnapshot engine, LineClearAnimation animation) {
        return new GameSnapshot(engine, 400, 0, 2, 1_000_000_000, GameStatus.RUNNING, 0, animation);
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void itemAndRowDeletionShareTheWipeWithoutFlashingOrErasingUnaffectedCells(boolean colorBlind) {
        Cell[][] cells = new Board(10, 20).snapshot();
        for (int x = 1; x <= 5; x++) cells[17][x] = Cell.occupiedBy(x % 2 == 0 ? TetrominoType.J : TetrominoType.O);
        for (int x = 0; x < 10; x++) cells[19][x] = Cell.occupiedBy(TetrominoType.I);
        Board board = new Board(cells);
        List<RemovedCell> removed = new ArrayList<>();
        for (int x : new int[]{1, 3, 5}) {
            Position position = new Position(x, 17);
            removed.add(new RemovedCell(position, board.cellAt(position)));
            board.removeCell(position);
        }
        var clear = board.clearFullLines();
        var engine = new EngineSnapshot(board.snapshot(), null, Piece.of(TetrominoType.T), EnginePhase.RUNNING);
        var colors = ColorPalette.of(colorBlind);
        var renderer = new TextRenderer(colors, new InputMapper(Settings.defaults()));
        for (long elapsed : new long[]{0, 60_000_000L, 299_999_999L}) {
            var frame = renderer.render(snapshot(engine, new LineClearAnimation(clear.clearedRows(), removed, null, elapsed)));
            for (int x : new int[]{2, 4}) {
                assertCell(frame, x, 17, BlockGlyphs.of(TetrominoType.J));
                assertEquals(colors.background(), frame.styleAt(TextRenderer.columnOf(x), TextRenderer.rowOf(17)).background());
            }
            assertCell(frame, 0, 17, BlockGlyphs.EMPTY);
            for (int x : new int[]{1, 3, 5}) {
                if (elapsed == 0) {
                    assertEquals(Color.WHITE, frame.styleAt(TextRenderer.columnOf(x), TextRenderer.rowOf(17)).background());
                } else {
                    assertCell(frame, x, 17, x == 3 || elapsed == 299_999_999L ? BlockGlyphs.EMPTY : BlockGlyphs.of(TetrominoType.O));
                }
            }
            if (elapsed == 60_000_000L) {
                assertCell(frame, 4, 19, BlockGlyphs.EMPTY);
                assertCell(frame, 0, 19, BlockGlyphs.of(TetrominoType.I));
            }
        }
        var finished = renderer.render(snapshot(engine, null));
        assertCell(finished, 2, 18, BlockGlyphs.of(TetrominoType.J));
        assertCell(finished, 1, 18, BlockGlyphs.EMPTY);
    }

    private static void assertCell(TextFrame frame, int x, int y, String glyph) {
        int col = TextRenderer.columnOf(x);
        int row = TextRenderer.rowOf(y);
        StringBuilder actual = new StringBuilder();
        for (int i = 0; i < TextRenderer.CELL_WIDTH; i++) {
            if (!frame.isContinuation(col + i, row)) actual.appendCodePoint(frame.codePointAt(col + i, row));
        }
        assertEquals(glyph, actual.toString());
    }
}
