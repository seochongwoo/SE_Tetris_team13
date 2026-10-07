package team.tetris.ui.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import team.tetris.application.GameCommand;
import team.tetris.application.SinglePlayerSession;
import team.tetris.application.model.Settings;
import team.tetris.core.PlayerEngine;
import team.tetris.core.TetrominoType;
import team.tetris.ui.TestApplication;
import team.tetris.ui.input.InputMapper;
import team.tetris.ui.render.palette.ColorPalette;

class TextRendererTest {

    private final ColorPalette palette = ColorPalette.standard();
    private final TextRenderer renderer = new TextRenderer(palette, new InputMapper(Settings.defaults()));
    private final SinglePlayerSession session =
            new SinglePlayerSession(new PlayerEngine(10, 20, TestApplication.constant(TetrominoType.T)));

    @Test
    void drawsABorderAroundATenByTwentyBoard() {
        TextFrame frame = renderer.render(session.snapshot());

        assertEquals('┌', frame.codePointAt(TextRenderer.BOARD_LEFT, TextRenderer.BOARD_TOP));
        assertEquals('┘', frame.codePointAt(TextRenderer.BOARD_LEFT + 21, TextRenderer.BOARD_TOP + 21));
        assertEquals(TextRenderer.columnOf(0), TextRenderer.BOARD_LEFT + 1);
        assertEquals(TextRenderer.rowOf(19), TextRenderer.BOARD_TOP + 20);
    }

    @Test
    void drawsTheFallingPieceOnTopOfTheBoardWithItsColorAndPattern() {
        // T(회전0)는 origin (3,0)에서 (4,0) (3,1) (4,1) (5,1) 칸을 차지한다.
        TextFrame frame = renderer.render(session.snapshot());

        int col = TextRenderer.columnOf(4);
        int row = TextRenderer.rowOf(0);
        assertEquals('<', frame.codePointAt(col, row));
        assertEquals('>', frame.codePointAt(col + 1, row));
        assertEquals(palette.block(TetrominoType.T), frame.styleAt(col, row).foreground());
        assertEquals('.', frame.codePointAt(TextRenderer.columnOf(0) + 1, TextRenderer.rowOf(0)));
        assertEquals(palette.dim(), frame.styleAt(TextRenderer.columnOf(0), TextRenderer.rowOf(19)).foreground());
    }

    @Test
    void drawsLockedCellsFromTheBoard() {
        session.handle(GameCommand.HARD_DROP);

        TextFrame frame = renderer.render(session.snapshot());

        assertTrue(frame.rowText(TextRenderer.rowOf(19)).contains("<><><>"));
    }

    @Test
    void showsTheNextPieceInsideThePreviewBox() {
        TextFrame frame = renderer.render(session.snapshot());

        boolean found = false;
        for (int row = TextRenderer.BOARD_TOP + 2; row < TextRenderer.BOARD_TOP + 6; row++) {
            found |= frame.rowText(row).substring(TextRenderer.PANEL_LEFT).contains("<>");
        }
        assertTrue(found);
        assertTrue(frame.rowText(TextRenderer.BOARD_TOP).contains("NEXT"));
    }

    @Test
    void showsScoreLevelLinesAndTheActualKeyBindings() {
        session.handle(GameCommand.HARD_DROP);

        String text = renderer.render(session.snapshot()).text();

        assertTrue(text.contains("SCORE"));
        assertTrue(text.contains("LEVEL"));
        assertTrue(text.contains("LINES"));
        assertTrue(text.contains(Long.toString(session.snapshot().score())));
        assertTrue(text.contains("Space 떨어뜨리기"));
        assertTrue(text.contains("← → 이동"));
        assertTrue(text.contains("Esc 메뉴"));
    }
}
