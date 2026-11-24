package team.tetris.application.battle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import team.tetris.application.GameCommand;
import team.tetris.application.model.Difficulty;
import team.tetris.core.ActivePiece;
import team.tetris.core.Board;
import team.tetris.core.Cell;
import team.tetris.core.EnginePhase;
import team.tetris.core.EngineSnapshot;
import team.tetris.core.Piece;
import team.tetris.core.Position;
import team.tetris.core.TetrominoType;
import team.tetris.core.item.SoloBlock;
import team.tetris.core.item.WeightBlock;
import team.tetris.core.rule.DropRule;
import team.tetris.core.rule.RotationResult;
import team.tetris.core.rule.RotationSystem;

/** 컴퓨터 대전의 상대 (도전 과제 CH-1): 보드 상태를 받아 다음 키 하나를 돌려준다. */
class ComputerPlayerTest {

    private static final int WIDTH = 10;
    private static final int HEIGHT = 20;
    private final ComputerPlayer computer = new ComputerPlayer();

    /** 아래 rows줄을 holeColumn 한 칸만 비우고 채운 보드. */
    private static Cell[][] wellBoard(int rows, int holeColumn) {
        Cell[][] cells = emptyBoard();
        for (int y = HEIGHT - rows; y < HEIGHT; y++) {
            for (int x = 0; x < WIDTH; x++) {
                if (x != holeColumn) {
                    cells[y][x] = Cell.occupiedBy(TetrominoType.O);
                }
            }
        }
        return cells;
    }

    private static Cell[][] emptyBoard() {
        Cell[][] cells = new Cell[HEIGHT][WIDTH];
        for (Cell[] row : cells) {
            Arrays.fill(row, Cell.EMPTY);
        }
        return cells;
    }

    private static EngineSnapshot running(Cell[][] cells, ActivePiece active) {
        return new EngineSnapshot(cells, active, Piece.of(TetrominoType.T), EnginePhase.RUNNING);
    }

    /**
     * 엔진처럼 키를 적용하며 컴퓨터가 낙하 키를 누를 때까지 따라간다. 떨어뜨린 블록이 놓인 칸을 돌려준다.
     * 키를 너무 많이 누르면(목표가 흔들리면) 실패한다.
     */
    private Set<Position> playOnePiece(Cell[][] cells, ActivePiece active) {
        Board board = new Board(cells);
        RotationSystem rotations = new RotationSystem();
        for (int presses = 0; presses < 12; presses++) {
            GameCommand command = computer.nextCommand(running(cells, active)).orElseThrow();
            switch (command) {
                case ROTATE_CW -> {
                    RotationResult result = rotations.tryRotate(board, active.shape(), active.rotation(),
                            active.origin(), true);
                    assertTrue(result.rotated(), "computer only rotates when it can");
                    active = active.rotatedTo(result.rotation(), result.origin());
                }
                case MOVE_LEFT, MOVE_RIGHT -> {
                    ActivePiece moved = active.movedTo(active.origin().translate(
                            command == GameCommand.MOVE_LEFT ? -1 : 1, 0));
                    assertTrue(board.canPlace(moved.shape(), moved.rotation(), moved.origin()),
                            "computer only moves where the piece fits");
                    active = moved;
                }
                case HARD_DROP -> {
                    return new HashSet<>(Arrays.asList(DropRule.landingPiece(cells, active).cells()));
                }
                default -> throw new AssertionError("Unexpected key " + command);
            }
        }
        throw new AssertionError("The computer kept pressing keys without dropping");
    }

    @Test
    void anIPieceGoesUprightIntoTheWellAndClearsFourLines() {
        Cell[][] cells = wellBoard(4, 9);
        ActivePiece spawn = new ActivePiece(TetrominoType.I, 0, new Position(3, 1));

        Set<Position> landed = playOnePiece(cells, spawn);

        assertEquals(Set.of(new Position(9, 16), new Position(9, 17), new Position(9, 18), new Position(9, 19)),
                landed);
    }

    @Test
    void thePieceIsMovedLeftWhenTheGapIsOnTheLeft() {
        Cell[][] cells = wellBoard(1, 0);
        ActivePiece spawn = new ActivePiece(TetrominoType.I, 0, new Position(3, 1));

        assertEquals(Optional.of(GameCommand.ROTATE_CW), computer.nextCommand(running(cells, spawn)));
        Set<Position> landed = playOnePiece(cells, spawn);

        assertTrue(landed.contains(new Position(0, HEIGHT - 1)), landed.toString());
    }

    @Test
    void onAFlatBoardAnOPieceIsDroppedWithoutTurningOrHolesAndTheChoiceIsStable() {
        Cell[][] cells = emptyBoard();
        ActivePiece spawn = new ActivePiece(TetrominoType.O, 0, new Position(3, 0));

        Set<Position> first = playOnePiece(cells, spawn);
        Set<Position> second = playOnePiece(cells, spawn);

        assertEquals(first, second, "same board and piece, same choice");
        for (Position cell : first) {
            assertEquals(true, cell.y() >= HEIGHT - 2, "lies on the floor: " + first);
        }
    }

    @Test
    void thePlanDoesNotChangeAfterEachKeySoItNeverWobbles() {
        // 회전·이동 한 번마다 다시 불러도 같은 자리로 가므로, 놓기까지 누르는 키 수가 회전 수 + 이동 거리 + 1이다.
        Cell[][] cells = wellBoard(2, 8);
        for (TetrominoType type : TetrominoType.values()) {
            ActivePiece spawn = new ActivePiece(type, 0, new Position(3, 1));
            Set<Position> landed = playOnePiece(cells, spawn);
            assertEquals(4, landed.size(), type.name());
        }
    }

    @Test
    void neverCoversTheOpenGapWithAnyPiece() {
        // 맨 아랫줄 가운데 한 칸이 비어 있다. 어떤 블록이든 그 위를 덮어 막힌 빈칸을 만들지 않는다.
        Cell[][] cells = wellBoard(1, 4);
        for (TetrominoType type : TetrominoType.values()) {
            Board after = new Board(cells);
            for (Position cell : playOnePiece(cells, new ActivePiece(type, 0, new Position(3, 1)))) {
                after.place(new ActivePiece(team.tetris.core.item.SoloBlock.SHAPE, 0, cell));
            }
            assertEquals(0, ComputerPlayer.holes(after, ComputerPlayer.columnHeights(after)), type.name());
        }
    }

    @Test
    void itemPiecesWithASingleShapeAreOnlyMovedAndDropped() {
        Cell[][] cells = wellBoard(1, 4);
        ActivePiece bomb = new ActivePiece(new Piece(SoloBlock.SHAPE, java.util.Map.of()), 0, new Position(0, 0));
        assertEquals(Set.of(new Position(4, HEIGHT - 1)), playOnePiece(cells, bomb));

        ActivePiece weight = new ActivePiece(new Piece(WeightBlock.SHAPE, java.util.Map.of()), 0, new Position(3, 0));
        assertEquals(4, playOnePiece(emptyBoard(), weight).stream().filter(p -> p.y() == HEIGHT - 1).count());
    }

    @Test
    void doesNothingWhenNoPieceIsFalling() {
        Cell[][] cells = emptyBoard();
        ActivePiece piece = new ActivePiece(TetrominoType.T, 0, new Position(3, 0));
        for (EnginePhase phase : new EnginePhase[] {EnginePhase.READY, EnginePhase.PAUSED, EnginePhase.GAME_OVER}) {
            assertEquals(Optional.empty(), computer.nextCommand(
                    new EngineSnapshot(cells, piece, Piece.of(TetrominoType.T), phase)), phase.name());
        }
        assertEquals(Optional.empty(), computer.nextCommand(running(cells, null)));
    }

    @Test
    void dropsAtOnceWhenNoPlaceIsReachable() {
        // 보드가 꽉 차 어디에 놓아도 위로 삐져나오면 옮기지 않고 그대로 떨어뜨린다.
        Cell[][] cells = emptyBoard();
        for (int y = 0; y < HEIGHT; y++) {
            for (int x = 0; x < WIDTH; x++) {
                cells[y][x] = Cell.occupiedBy(TetrominoType.O);
            }
        }
        ActivePiece stuck = new ActivePiece(TetrominoType.T, 0, new Position(3, -5));
        assertEquals(Optional.of(GameCommand.HARD_DROP), computer.nextCommand(running(cells, stuck)));
    }

    @Test
    void aPlaceThatSticksOutOfTheTopIsTheWorstChoice() {
        Cell[][] cells = wellBoard(HEIGHT - 1, 0);
        ActivePiece high = new ActivePiece(TetrominoType.O, 0, new Position(3, -1));
        assertTrue(ComputerPlayer.evaluate(cells, high) < -1000);
    }

    @ParameterizedTest
    @EnumSource(Difficulty.class)
    void pressesAKeyEveryHalfToOneSecondFasterOnHarderLevels(Difficulty difficulty) {
        long interval = ComputerPlayer.intervalNanos(difficulty);
        assertTrue(interval >= 500_000_000L && interval <= 1_000_000_000L, difficulty.name());
        if (difficulty != Difficulty.EASY) {
            assertTrue(interval < ComputerPlayer.intervalNanos(Difficulty.values()[difficulty.ordinal() - 1]));
        }
    }

    // ---- NEXT 블록까지 보기 (어려움) ----

    /** 긴 게임의 결과: 놓은 블록 수(지면 그때까지), 지운 줄, 가장 높이 쌓인 높이, 블록마다 센 막힌 빈칸의 합. */
    private record Run(int pieces, int lines, int maxHeight, int holes) {
    }

    /** 정해진 블록 순서로 pieces개를 놓아 본다. 중력 없이 컴퓨터가 고른 키만 적용한다. */
    private static Run play(ComputerPlayer player, int pieces, long seed) {
        java.util.Random random = new java.util.Random(seed);
        TetrominoType[] types = TetrominoType.values();
        TetrominoType current = types[random.nextInt(types.length)];
        Board board = new Board(WIDTH, HEIGHT);
        RotationSystem rotations = new RotationSystem();
        int lines = 0;
        int maxHeight = 0;
        int holes = 0;
        for (int i = 0; i < pieces; i++) {
            TetrominoType next = types[random.nextInt(types.length)];
            ActivePiece active = new ActivePiece(current, 0, new Position(3, 1));
            if (!board.canPlace(current, 0, active.origin())) {
                return new Run(i, lines, maxHeight, holes);
            }
            for (int presses = 0; ; presses++) {
                assertTrue(presses < 12, "drops within a few keys");
                Cell[][] cells = board.snapshot();
                GameCommand command = player.nextCommand(new EngineSnapshot(cells, active, Piece.of(next),
                        EnginePhase.RUNNING)).orElseThrow();
                if (command == GameCommand.HARD_DROP) {
                    ActivePiece landing = DropRule.landingPiece(cells, active);
                    for (Position cell : landing.cells()) {
                        if (cell.y() < 0) {
                            return new Run(i, lines, maxHeight, holes);
                        }
                    }
                    board.place(landing);
                    lines += board.clearFullLines().lineCount();
                    break;
                }
                if (command == GameCommand.ROTATE_CW) {
                    RotationResult result = rotations.tryRotate(board, active.shape(), active.rotation(),
                            active.origin(), true);
                    active = active.rotatedTo(result.rotation(), result.origin());
                } else {
                    active = active.movedTo(active.origin().translate(command == GameCommand.MOVE_LEFT ? -1 : 1, 0));
                }
            }
            int[] heights = ComputerPlayer.columnHeights(board);
            for (int height : heights) {
                maxHeight = Math.max(maxHeight, height);
            }
            holes += ComputerPlayer.holes(board, heights);
            current = next;
        }
        return new Run(pieces, lines, maxHeight, holes);
    }

    @Test
    void onlyTheHardComputerLooksAtTheNextPiece() {
        assertFalse(ComputerPlayer.forDifficulty(Difficulty.EASY).looksAhead());
        assertFalse(ComputerPlayer.forDifficulty(Difficulty.NORMAL).looksAhead());
        assertTrue(ComputerPlayer.forDifficulty(Difficulty.HARD).looksAhead());
        assertFalse(new ComputerPlayer().looksAhead());
    }

    @Test
    void lookingAtTheNextPieceKeepsTheBoardLowerWithFewerHolesOverLongGames() {
        // 같은 블록 순서 6가지로 300개씩 놓는다. NEXT까지 보는 쪽은 모두 살아남고, 막힌 빈칸이 훨씬 적고
        // 가장 높이 쌓인 높이도 낮다. (지운 줄 수는 비슷하다: 놓은 칸 수가 같으므로)
        int plainHoles = 0;
        int aheadHoles = 0;
        int plainWorst = 0;
        int aheadWorst = 0;
        for (long seed = 1; seed <= 6; seed++) {
            Run plain = play(new ComputerPlayer(false), 300, seed);
            Run ahead = play(new ComputerPlayer(true), 300, seed);
            assertEquals(300, ahead.pieces(), "seed " + seed + " survives");
            assertTrue(ahead.lines() > 100, "seed " + seed + ": " + ahead);
            plainHoles += plain.holes();
            aheadHoles += ahead.holes();
            plainWorst = Math.max(plainWorst, plain.maxHeight());
            aheadWorst = Math.max(aheadWorst, ahead.maxHeight());
        }
        assertTrue(aheadHoles * 2 < plainHoles, aheadHoles + " / " + plainHoles);
        assertTrue(aheadWorst < plainWorst, aheadWorst + " / " + plainWorst);
    }

    @Test
    void theNextPieceChangesWhereTheCurrentOneGoes() {
        // 오른쪽 끝에 깊이 4의 우물. 지금 블록이 O이고 다음이 I면, O를 우물 옆에 두어 I 자리를 남긴다.
        Cell[][] cells = wellBoard(4, 9);
        ActivePiece o = new ActivePiece(TetrominoType.O, 0, new Position(3, 0));
        var withI = new ComputerPlayer(true).bestTarget(cells, o, Piece.of(TetrominoType.I));
        var plain = new ComputerPlayer(false).bestTarget(cells, o);
        assertTrue(withI.score() > plain.score(), "I가 우물에 들어가 4줄을 지우는 수를 본다");
        assertTrue(withI.x() + 2 <= 9, "우물을 막지 않는다");
        assertEquals(-1_000_000, ComputerPlayer.evaluateWithNext(wellBoard(HEIGHT, 0), o, Piece.of(TetrominoType.I)),
                "놓을 곳이 없으면 지는 자리");
    }

    @Test
    void theHardComputerStillWorksWithItemPiecesAsTheNextPiece() {
        Cell[][] cells = wellBoard(1, 4);
        ActivePiece t = new ActivePiece(TetrominoType.T, 0, new Position(3, 1));
        for (Piece next : java.util.List.of(new Piece(SoloBlock.SHAPE, java.util.Map.of()),
                new Piece(WeightBlock.SHAPE, java.util.Map.of()))) {
            assertTrue(new ComputerPlayer(true).nextCommand(new EngineSnapshot(cells, t, next, EnginePhase.RUNNING))
                    .isPresent());
        }
    }

    // ---- 난이도별 실수 ----

    @Test
    void easierLevelsMakeMoreMistakesAndOnlyHardLooksAhead() {
        assertEquals(0.35, ComputerPlayer.forDifficulty(Difficulty.EASY).mistakeRate());
        assertEquals(0.15, ComputerPlayer.forDifficulty(Difficulty.NORMAL).mistakeRate());
        assertEquals(0.0, ComputerPlayer.forDifficulty(Difficulty.HARD).mistakeRate());
        for (double rate : new double[] {-0.1, 1.1}) {
            org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                    () -> new ComputerPlayer(false, rate, 3));
        }
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> new ComputerPlayer(false, 0.5, 0));
    }

    @Test
    void harderLevelsLastLongerOverTheSamePieceSequences() {
        // 같은 블록 순서 6가지로 300개씩. 어려움은 모두 버티고, 쉬움 < 보통 < 어려움 순으로 오래 버틴다.
        int[] total = new int[Difficulty.values().length];
        for (long seed = 1; seed <= 6; seed++) {
            for (Difficulty difficulty : Difficulty.values()) {
                Run run = play(ComputerPlayer.forDifficulty(difficulty), 300, seed);
                total[difficulty.ordinal()] += run.pieces();
                if (difficulty == Difficulty.HARD) {
                    assertEquals(300, run.pieces(), "seed " + seed);
                }
            }
        }
        assertTrue(total[0] < total[1] && total[1] < total[2], Arrays.toString(total));
    }

    @Test
    void aMistakeStaysTheSameWhileOnePieceFallsAndNeverPicksALosingPlace() {
        // 실수할 확률이 1이어도 블록 하나를 놓는 동안 목표가 흔들리지 않고(키 12번 안에 낙하), 1등이 아닌 자리에 놓는다.
        ComputerPlayer clumsy = new ComputerPlayer(false, 1.0, 3);
        Cell[][] cells = wellBoard(3, 6);
        ActivePiece spawn = new ActivePiece(TetrominoType.T, 0, new Position(3, 1));
        ComputerPlayer.Target best = clumsy.bestTarget(cells, spawn);
        ComputerPlayer.Target chosen = clumsy.chooseTarget(cells, spawn, null);
        assertTrue(chosen.score() < best.score() && chosen.score() > -1_000_000, chosen + " / " + best);
        assertEquals(chosen, clumsy.chooseTarget(cells, spawn, null), "같은 보드·블록이면 같은 선택");
        for (long seed = 1; seed <= 3; seed++) {
            assertTrue(play(clumsy, 40, seed).pieces() > 0);
        }
        // 고를 자리가 하나뿐이면 실수할 수 없다.
        Cell[][] narrow = new Cell[HEIGHT][1];
        for (Cell[] row : narrow) {
            row[0] = Cell.EMPTY;
        }
        ActivePiece solo = new ActivePiece(new Piece(SoloBlock.SHAPE, java.util.Map.of()), 0, new Position(0, 0));
        assertEquals(clumsy.bestTarget(narrow, solo), clumsy.chooseTarget(narrow, solo, null));
    }

    @Test
    void theMistakeRateIsRoughlyWhatWasAskedFor() {
        // 여러 보드에서 실제로 1등이 아닌 자리를 고르는 비율이 정한 확률 근처다.
        ComputerPlayer easy = ComputerPlayer.forDifficulty(Difficulty.EASY);
        java.util.Random random = new java.util.Random(11);
        int mistakes = 0;
        int trials = 400;
        for (int t = 0; t < trials; t++) {
            Cell[][] cells = emptyBoard();
            for (int x = 0; x < WIDTH; x++) {
                for (int y = HEIGHT - random.nextInt(5); y < HEIGHT; y++) {
                    cells[y][x] = Cell.occupiedBy(TetrominoType.O);
                }
            }
            TetrominoType type = TetrominoType.values()[random.nextInt(7)];
            ActivePiece spawn = new ActivePiece(type, 0, new Position(3, 1));
            if (!easy.chooseTarget(cells, spawn, null).equals(easy.bestTarget(cells, spawn))) {
                mistakes++;
            }
        }
        assertTrue(mistakes > trials * 0.25 && mistakes < trials * 0.45, mistakes + " / " + trials);
    }
}
