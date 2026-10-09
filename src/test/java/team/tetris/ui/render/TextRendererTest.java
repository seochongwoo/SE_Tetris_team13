package team.tetris.ui.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import team.tetris.application.GameCommand;
import team.tetris.application.GameSnapshot;
import team.tetris.application.GameStatus;
import team.tetris.application.SinglePlayerSession;
import team.tetris.application.model.Settings;
import team.tetris.core.Cell;
import team.tetris.core.EngineSnapshot;
import team.tetris.core.Piece;
import team.tetris.core.PlayerEngine;
import team.tetris.core.Position;
import team.tetris.core.Shape;
import team.tetris.core.TetrominoType;
import team.tetris.core.item.Item;
import team.tetris.core.item.ItemContext;
import team.tetris.core.rule.PieceSource;
import team.tetris.ui.TestApplication;
import team.tetris.ui.input.InputMapper;
import team.tetris.ui.render.palette.BlockGlyphs;
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
    /** 화면 표시 확인용 아이템 (효과 없음). */
    private static final Item KEY = new Item() {
        @Override
        public char symbol() {
            return 'K';
        }

        @Override
        public void onLock(ItemContext context, Position position) {
        }
    };

    /** 항상 0번 칸에 아이템이 실린 T만 내놓는 공급자. */
    private static PieceSource tWithItem() {
        Piece piece = Piece.of(TetrominoType.T).withItem(0, KEY);
        return new PieceSource() {
            @Override
            public Piece next() {
                return piece;
            }

            @Override
            public Piece peek() {
                return piece;
            }
        };
    }

    @Test
    void anItemCellShowsItsSymbolInInvertedColorsOnTheFallingPieceAndInThePreview() {
        SinglePlayerSession withItem = new SinglePlayerSession(new PlayerEngine(10, 20, tWithItem()));

        TextFrame frame = renderer.render(withItem.snapshot());

        // T(회전0)의 0번 칸은 origin (3,0) 기준 (4,0).
        int col = TextRenderer.columnOf(4);
        int row = TextRenderer.rowOf(0);
        assertEquals('K', frame.codePointAt(col, row));
        assertEquals(palette.itemStyle(TetrominoType.T), frame.styleAt(col, row));
        assertEquals('<', frame.codePointAt(TextRenderer.columnOf(3), TextRenderer.rowOf(1)));
        boolean previewHasItem = false;
        for (int r = TextRenderer.BOARD_TOP + 2; r < TextRenderer.BOARD_TOP + 6; r++) {
            previewHasItem |= frame.rowText(r).substring(TextRenderer.PANEL_LEFT).contains("K");
        }
        assertTrue(previewHasItem);
    }

    @Test
    void lockedCellsKeepTheirItemSymbolAndNonTetrominoBlocksUseTheSpecialPattern() {
        Shape weight = new Shape() {
            @Override
            public Position[] cellsAt(int rotation) {
                return new Position[] {new Position(0, 0)};
            }

            @Override
            public int rotationStates() {
                return 1;
            }
        };
        Cell[][] board = session.snapshot().engine().board();
        board[19][0] = Cell.withItem(TetrominoType.I, KEY);
        board[19][1] = Cell.occupiedBy(weight);
        EngineSnapshot engine = session.snapshot().engine();
        GameSnapshot snapshot = new GameSnapshot(
                new EngineSnapshot(board, engine.activePiece(), engine.nextPiece(), engine.phase()),
                0, 0, 0, 1_000_000_000L, GameStatus.RUNNING);

        TextFrame frame = renderer.render(snapshot);

        int row = TextRenderer.rowOf(19);
        assertEquals('K', frame.codePointAt(TextRenderer.columnOf(0), row));
        assertEquals(palette.itemStyle(TetrominoType.I), frame.styleAt(TextRenderer.columnOf(0), row));
        assertEquals(BlockGlyphs.SPECIAL, frame.rowText(row).substring(TextRenderer.columnOf(1), TextRenderer.columnOf(2)));
        assertEquals(palette.text(), frame.styleAt(TextRenderer.columnOf(1), row).foreground());
    }
}
