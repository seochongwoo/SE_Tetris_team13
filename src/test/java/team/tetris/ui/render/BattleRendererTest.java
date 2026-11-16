package team.tetris.ui.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import team.tetris.application.GameSnapshot;
import team.tetris.application.GameStatus;
import team.tetris.application.battle.BattlePlayer;
import team.tetris.application.battle.BattleSnapshot;
import team.tetris.application.battle.BattleStatus;
import team.tetris.application.model.Difficulty;
import team.tetris.application.model.GameMode;
import team.tetris.application.model.Settings;
import team.tetris.core.ActivePiece;
import team.tetris.core.AttackBlock;
import team.tetris.core.Cell;
import team.tetris.core.EnginePhase;
import team.tetris.core.EngineSnapshot;
import team.tetris.core.Piece;
import team.tetris.core.Position;
import team.tetris.core.TetrominoType;
import team.tetris.ui.input.BattleKeys;
import team.tetris.ui.render.palette.ColorPalette;

/** 대전 화면: 보드 2개, 각자의 NEXT·점수, 받을 줄 영역, 조작 안내 (3차 카드 19, R3-07, H3-02). */
class BattleRendererTest {

    private static final Cell ATTACK = Cell.occupiedBy(AttackBlock.INSTANCE);

    private static Cell[][] board() {
        Cell[][] board = new Cell[20][10];
        for (Cell[] row : board) {
            Arrays.fill(row, Cell.EMPTY);
        }
        return board;
    }

    private static GameSnapshot game(Cell[][] board, long score, int level, int lines) {
        EngineSnapshot engine = new EngineSnapshot(board, new ActivePiece(TetrominoType.T, 0, new Position(3, 0)),
                Piece.of(TetrominoType.I), EnginePhase.RUNNING);
        return new GameSnapshot(engine, score, level, lines, 1_000_000_000L, GameStatus.RUNNING, 0, null,
                GameMode.NORMAL, Difficulty.HARD);
    }

    private static List<Cell> attackLine(int hole) {
        List<Cell> line = new ArrayList<>(Collections.nCopies(10, ATTACK));
        line.set(hole, Cell.EMPTY);
        return line;
    }

    private static BattleSnapshot snapshot(Cell[][] one, Cell[][] two, List<List<Cell>> pendingTwo) {
        return new BattleSnapshot(game(one, 1234, 2, 15), game(two, 50, 0, 3), List.of(), pendingTwo,
                BattleStatus.RUNNING, null, GameMode.ITEM, Difficulty.HARD);
    }

    private static BattleRenderer renderer(boolean colorBlind) {
        return new BattleRenderer(ColorPalette.of(colorBlind), BattleKeys.defaults(Settings.defaults()));
    }

    @Test
    void bothBoardsAreDrawnSideBySideOnAWiderGrid() {
        Cell[][] one = board();
        one[19][0] = Cell.occupiedBy(TetrominoType.O);
        Cell[][] two = board();
        two[19][9] = Cell.occupiedBy(TetrominoType.Z);
        BattleRenderer renderer = renderer(false);

        TextFrame frame = renderer.render(snapshot(one, two, List.of()));

        assertEquals(BattleRenderer.COLUMNS, frame.columns());
        assertEquals(TextFrame.ROWS, frame.rows());
        BoardPainter left = renderer.boardOf(BattlePlayer.ONE);
        BoardPainter right = renderer.boardOf(BattlePlayer.TWO);
        assertEquals(ColorPalette.standard().block(TetrominoType.O),
                frame.tileAt(left.columnOf(0), left.rowOf(19)).color());
        assertEquals(ColorPalette.standard().block(TetrominoType.Z),
                frame.tileAt(right.columnOf(9), right.rowOf(19)).color());
        assertEquals(BlockTile.Kind.EMPTY, frame.tileAt(right.columnOf(0), right.rowOf(19)).kind());
        // 두 보드의 활성 블록(T)이 각자 보드 위쪽에 그려진다.
        assertEquals(ColorPalette.standard().block(TetrominoType.T),
                frame.tileAt(left.columnOf(4), left.rowOf(0)).color());
        assertEquals(ColorPalette.standard().block(TetrominoType.T),
                frame.tileAt(right.columnOf(4), right.rowOf(0)).color());
        assertTrue(right.columnOf(0) > left.columnOf(9) + 14, "2P 보드는 1P 정보 영역 오른쪽에 있다");
    }

    @Test
    void eachPlayerHasALabelNextPieceScoreAndLines() {
        TextFrame frame = renderer(false).render(snapshot(board(), board(), List.of()));
        String text = frame.text();

        assertTrue(frame.rowText(1).contains("1P") && frame.rowText(1).contains("2P"));
        assertTrue(frame.rowText(1).contains("LV 2") && frame.rowText(1).contains("LV 0"));
        assertEquals(2, count(text, "NEXT"));
        assertTrue(text.contains("1234") && text.contains("15"));
        assertTrue(text.contains("50"));
        assertTrue(frame.rowText(0).contains("대전 · 아이템 / 어려움"));
        assertTrue(frame.rowText(0).contains("P 일시정지") && frame.rowText(0).contains("Esc 메뉴"));
    }

    @Test
    void controlsForEachPlayerAreListedUnderTheirBoard() {
        TextFrame frame = renderer(false).render(snapshot(board(), board(), List.of()));
        String help = frame.rowText(TextFrame.ROWS - 1);

        assertTrue(help.contains("1P A D W S · Space"), help);
        assertTrue(help.contains("2P ← → ↑ ↓ · Enter"), help);
    }

    @Test
    void waitingAttackLinesAreShownBottomAlignedInTheReceiversArea() {
        // R3-07: 넘어간 줄은 상대 보드에 들어가기 전에 받을 줄 영역에 먼저 보인다.
        TextFrame frame = renderer(false).render(snapshot(board(), board(), List.of(attackLine(0), attackLine(9))));

        assertTrue(frame.text().contains("0/10"), "1P는 받을 줄이 없다");
        assertTrue(frame.text().contains("2/10"));
        int boxBottom = TextFrame.ROWS - 1 - 1; // 상자 테두리 바로 위가 마지막 줄
        int col = BattleRenderer.leftOf(BattlePlayer.TWO) + 23 + 1;
        assertEquals("█████████·", cells(frame, col, boxBottom));
        assertEquals("·█████████", cells(frame, col, boxBottom - 1));
        assertEquals(" ".repeat(10), cells(frame, col, boxBottom - 2));
        assertEquals(ColorPalette.standard().blockStyle(AttackBlock.INSTANCE), frame.styleAt(col, boxBottom));
    }

    @ParameterizedTest
    @ValueSource(longs = {1234, 12345678})
    void doubleScoreIndicatorDoesNotOverwriteEitherPlayersScore(long score) {
        // 리뷰 지적: 점수 2배 표시가 네 자리 이상 점수의 첫 자리를 덮었다.
        GameSnapshot base = game(board(), score, 2, 15);
        GameSnapshot boosted = new GameSnapshot(base.engine(), score, 2, 15,
                base.gravityIntervalNanos(), GameStatus.RUNNING, 3, null, GameMode.ITEM, Difficulty.HARD);
        TextFrame frame = renderer(false).render(new BattleSnapshot(boosted, boosted, List.of(), List.of(),
                BattleStatus.RUNNING, null, GameMode.ITEM, Difficulty.HARD));

        String scoreRow = frame.rowText(2 + BoardPainter.NEXT_BOX_HEIGHT + 1);
        assertEquals(2, count(scoreRow, Long.toString(score)));
        assertEquals(2, count(scoreRow, "SCORE"));
        assertEquals(2, count(frame.rowText(2), "NEXT"));
        assertEquals(2, count(frame.rowText(2), "x2(3)"));
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void attackCellsOnTheBoardAreGreyWithTheirOwnPattern(boolean colorBlind) {
        // H3-02: 색맹 모드에서도 직접 쌓은 칸과 구분되도록 회색 + 전용 무늬.
        Cell[][] one = board();
        one[19] = attackLine(3).toArray(Cell[]::new);
        one[18][0] = Cell.occupiedBy(TetrominoType.L);
        BattleRenderer renderer = renderer(colorBlind);

        TextFrame frame = renderer.render(snapshot(one, board(), List.of()));

        BoardPainter left = renderer.boardOf(BattlePlayer.ONE);
        BlockTile attack = frame.tileAt(left.columnOf(0), left.rowOf(19));
        BlockTile block = frame.tileAt(left.columnOf(0), left.rowOf(18));
        assertEquals(BlockTile.Pattern.BARS, attack.pattern());
        assertNotEquals(block.pattern(), attack.pattern());
        assertNotEquals(block.color(), attack.color());
        assertEquals(attack.color().getRed(), attack.color().getGreen());
    }

    @Test
    void aTimedBattleShowsTheRemainingTimeAndWarnsInTheLastTenSeconds() {
        BattleSnapshot base = snapshot(board(), board(), List.of());
        BattleSnapshot timed = new BattleSnapshot(base.one(), base.two(), List.of(), List.of(), BattleStatus.RUNNING,
                null, GameMode.NORMAL, Difficulty.NORMAL,
                new team.tetris.application.battle.BattleTimer(120_000_000_000L, 9_500_000_000L));

        TextFrame frame = renderer(false).render(timed);

        String header = frame.rowText(0);
        assertTrue(header.contains("대전 · 시간제한 / 보통"), header);
        assertTrue(header.contains("남은 시간 0:10"), header);
        boolean warned = false;
        for (int col = 0; col < frame.columns(); col++) {
            warned |= frame.codePointAt(col, 0) == ':'
                    && frame.styleAt(col, 0).equals(ColorPalette.standard().warningStyle());
        }
        assertTrue(warned, "마지막 10초는 경고색");
        assertEquals("2:05", BattleRenderer.clock(125));
        assertEquals("0:00", BattleRenderer.clock(0));
        assertFalse(renderer(false).render(base).rowText(0).contains("남은 시간"));
    }

    /** col부터 열 칸의 글자 (행 문자열은 넓은 글자 때문에 열 위치와 어긋날 수 있다). */
    private static String cells(TextFrame frame, int col, int row) {
        StringBuilder text = new StringBuilder();
        for (int x = col; x < col + 10; x++) {
            text.appendCodePoint(frame.codePointAt(x, row));
        }
        return text.toString();
    }

    private static int count(String text, String part) {
        int count = 0;
        for (int i = text.indexOf(part); i >= 0; i = text.indexOf(part, i + 1)) {
            count++;
        }
        return count;
    }
}
