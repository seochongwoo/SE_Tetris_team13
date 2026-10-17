package team.tetris.core.item;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;
import team.tetris.core.ActivePiece;
import team.tetris.core.Cell;
import team.tetris.core.GameAction;
import team.tetris.core.Piece;
import team.tetris.core.PlayerEngine;
import team.tetris.core.Position;
import team.tetris.core.TetrominoType;
import team.tetris.core.result.EngineStep;
import team.tetris.core.rule.PieceSource;

/**
 * 무게추가 실제 엔진에서 요구사항대로 움직이는지 확인한다.
 *
 * <p>폭 6 보드에서 O는 x=2~3, 무게추는 아래 줄 x=1~4 / 위 줄 x=2~3으로 스폰된다. 높이 8 보드의
 * 바닥 줄은 y=7이다.
 */
class WeightInEngineTest {

    private static final Piece WEIGHT = new WeightBlock().toItemPiece(Piece.of(TetrominoType.I), new Random(1));

    /** 정해 둔 블록을 순서대로 내놓고, 다 쓰면 O만 내놓는 공급자. */
    private static PieceSource sequence(Piece... pieces) {
        Deque<Piece> queue = new ArrayDeque<>(List.of(pieces));
        return new PieceSource() {
            @Override
            public Piece next() {
                Piece next = queue.poll();
                return next == null ? Piece.of(TetrominoType.O) : next;
            }

            @Override
            public Piece peek() {
                Piece next = queue.peek();
                return next == null ? Piece.of(TetrominoType.O) : next;
            }
        };
    }

    /** O 두 개를 바닥에 놓은 6×8 엔진: (2~3, 6~7)과 (4~5, 6~7)이 차 있고, 지금 블록은 무게추. */
    private static PlayerEngine stackThenWeight() {
        PlayerEngine engine = new PlayerEngine(6, 8,
                sequence(Piece.of(TetrominoType.O), Piece.of(TetrominoType.O), WEIGHT));
        engine.apply(GameAction.HARD_DROP);
        engine.apply(GameAction.MOVE_RIGHT);
        engine.apply(GameAction.MOVE_RIGHT);
        engine.apply(GameAction.HARD_DROP);
        assertSame(WeightBlock.SHAPE, active(engine).shape());
        return engine;
    }

    private static ActivePiece active(PlayerEngine engine) {
        return engine.snapshot().activePiece();
    }

    private static Cell cell(Cell[][] board, int x, int y) {
        return board[y][x];
    }

    @Test
    void theWeightIsFourCellsWideDoesNotRotateAndCarriesTheLetterW() {
        assertEquals(4, WEIGHT.shape().cellsAt(0)[5].x() - WEIGHT.shape().cellsAt(0)[2].x() + 1);
        assertEquals(1, WEIGHT.shape().rotationStates());
        assertEquals(6, WEIGHT.items().size());
        for (Item item : WEIGHT.items().values()) {
            assertEquals('W', item.symbol());
            assertTrue(item instanceof DrillingItem);
        }
    }

    @Test
    void beforeTouchingAnythingItMovesSidewaysButNeverRotates() {
        PlayerEngine engine = stackThenWeight();
        Position start = active(engine).origin();

        engine.apply(GameAction.MOVE_LEFT);
        assertEquals(start.translate(-1, 0), active(engine).origin());
        engine.apply(GameAction.MOVE_RIGHT);
        assertEquals(start, active(engine).origin());
        engine.apply(GameAction.ROTATE_CW);
        assertEquals(0, active(engine).rotation());
        assertEquals(start, active(engine).origin());
    }

    @Test
    void onceItTouchesABlockSidewaysMovesAreIgnoredWhileItDrillsDown() {
        PlayerEngine engine = stackThenWeight();
        for (int i = 0; i < 4; i++) {
            engine.tick(); // 아래 줄 y=1 → 5, 아직 닿지 않음
        }
        assertEquals(5, active(engine).origin().y() + 1);
        engine.apply(GameAction.MOVE_LEFT);
        engine.apply(GameAction.MOVE_RIGHT); // 닿기 전이므로 움직일 수 있다
        int x = active(engine).origin().x();

        EngineStep contact = engine.tick(); // O에 닿음 → 좌우 잠금, 한 줄 뚫고 내려감
        assertNull(contact.lockResult());
        assertEquals(6, active(engine).origin().y() + 1);

        engine.apply(GameAction.MOVE_LEFT);
        engine.apply(GameAction.MOVE_RIGHT);
        engine.apply(GameAction.MOVE_RIGHT);
        assertEquals(x, active(engine).origin().x(), "닿은 뒤에는 좌우로 움직이지 않는다");

        engine.apply(GameAction.SOFT_DROP); // 바닥 줄까지 뚫음
        assertEquals(7, active(engine).origin().y() + 1);
        engine.apply(GameAction.MOVE_LEFT);
        assertEquals(x, active(engine).origin().x());

        EngineStep landed = engine.tick(); // 더 내려갈 곳이 없으므로 고정
        assertSame(WeightBlock.SHAPE, landed.lockResult().shape());
        assertNotSame(WeightBlock.SHAPE, active(engine).shape(), "다음 블록은 다시 좌우로 움직일 수 있다");
        Position next = active(engine).origin();
        engine.apply(GameAction.MOVE_LEFT);
        assertEquals(next.translate(-1, 0), active(engine).origin());
    }

    @Test
    void itErasesEverythingBelowItInItsColumnsAndStopsOnTheFloor() {
        PlayerEngine engine = stackThenWeight();

        EngineStep step = engine.apply(GameAction.HARD_DROP);

        Cell[][] board = step.snapshot().board();
        // 무게추: 아래 줄 (1~4, 7), 위 줄 (2~3, 6)
        for (int x = 1; x <= 4; x++) {
            assertSame(WeightBlock.SHAPE, cell(board, x, 7).occupiedBy());
        }
        assertSame(WeightBlock.SHAPE, cell(board, 2, 6).occupiedBy());
        assertSame(WeightBlock.SHAPE, cell(board, 3, 6).occupiedBy());
        // 무게추가 지나간 자리의 O는 지워짐
        assertTrue(cell(board, 4, 6).isEmpty());
        // 무게추 폭 밖의 O는 남음
        assertEquals(TetrominoType.O, cell(board, 5, 6).occupiedBy());
        assertEquals(TetrominoType.O, cell(board, 5, 7).occupiedBy());
        // 고정된 무게추는 아이템 표시 없이 일반 칸으로 남음
        int filled = 0;
        for (Cell[] row : board) {
            for (Cell c : row) {
                assertFalse(c.hasItem());
                filled += c.isEmpty() ? 0 : 1;
            }
        }
        assertEquals(8, filled);
        assertTrue(step.clearResult().isEmpty());
    }

    @Test
    void itsFallIsNeverReportedForScoring() {
        PlayerEngine engine = new PlayerEngine(6, 8, sequence(WEIGHT));

        assertNull(engine.tick().dropResult());
        assertNull(engine.apply(GameAction.SOFT_DROP).dropResult());
        EngineStep step = engine.apply(GameAction.HARD_DROP);

        assertNull(step.dropResult());
        assertSame(WeightBlock.SHAPE, step.lockResult().shape());
    }

    @Test
    void landingOnAnEmptyFloorLocksItRightAway() {
        PlayerEngine engine = new PlayerEngine(6, 8, sequence(WEIGHT));
        for (int i = 0; i < 6; i++) {
            assertNull(engine.tick().lockResult()); // 아래 줄 y=1 → 7
        }

        EngineStep landed = engine.tick();

        assertSame(WeightBlock.SHAPE, landed.lockResult().shape());
        assertEquals(new Position(1, 6), landed.lockResult().origin());
    }

    @Test
    void rowsFilledByTheLandedWeightAreClearedAsUsual() {
        // 폭 4 보드에서는 무게추의 아래 줄이 한 줄을 다 채운다.
        PlayerEngine engine = new PlayerEngine(4, 6, sequence(WEIGHT));

        EngineStep step = engine.apply(GameAction.HARD_DROP);

        assertEquals(1, step.clearResult().lineCount());
        Cell[][] board = step.snapshot().board();
        assertSame(WeightBlock.SHAPE, board[5][1].occupiedBy()); // 위 줄 2칸이 한 줄 내려옴
        assertSame(WeightBlock.SHAPE, board[5][2].occupiedBy());
        assertTrue(board[5][0].isEmpty());
    }

    @Test
    void theWeightAppearsInItemModeFromTheCatalog() {
        assertTrue(ItemCatalog.all().stream().anyMatch(kind -> kind instanceof WeightBlock));
    }
}
