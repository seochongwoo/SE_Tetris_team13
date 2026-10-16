package team.tetris.ui.render;

import static org.junit.jupiter.api.Assertions.*;

import java.awt.Color;
import java.util.Set;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import team.tetris.application.GameSnapshot;
import team.tetris.application.GameStatus;
import team.tetris.application.LineClearAnimation;
import team.tetris.application.model.Settings;
import team.tetris.core.*;
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
                new LineClearAnimation(clear.clearedRows(), LineClearAnimation.FLASH_NANOS)));
        for (int y : new int[]{17, 19}) {
            for (int x = 0; x < 10 * TextRenderer.CELL_WIDTH; x++) {
                int col = TextRenderer.columnOf(0) + x;
                assertEquals(Color.WHITE, flashing.styleAt(col, TextRenderer.rowOf(y)).background());
                assertEquals(' ', hidden.codePointAt(col, TextRenderer.rowOf(y)));
                assertEquals(colors.background(), hidden.styleAt(col, TextRenderer.rowOf(y)).background());
            }
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

    private static GameSnapshot snapshot(EngineSnapshot engine, LineClearAnimation animation) {
        return new GameSnapshot(engine, 400, 0, 2, 1_000_000_000, GameStatus.RUNNING, 0, animation);
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
