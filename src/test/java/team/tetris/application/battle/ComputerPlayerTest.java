package team.tetris.application.battle;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
}
