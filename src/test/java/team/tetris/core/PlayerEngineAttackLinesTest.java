package team.tetris.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import org.junit.jupiter.api.Test;
import team.tetris.core.result.EngineStep;
import team.tetris.core.rule.IncomingLines;
import team.tetris.core.rule.PieceGenerator;
import team.tetris.core.rule.PieceSource;
import team.tetris.core.rule.PlainPieceSource;

/**
 * 대전의 공격 줄이 들어오는 순서와 넘침 판정. 블록 고정 → 아이템 효과·줄 삭제 → 공격 줄 추가 →
 * 넘침 판정 → 다음 블록 생성 (3차 R3-08, 안건 ⑩).
 */
class PlayerEngineAttackLinesTest {

    private static final Cell ATTACK = Cell.occupiedBy(AttackBlock.INSTANCE);
    private static final Cell EMPTY = Cell.EMPTY;

    /** 받을 공격 줄을 묶음 단위로 미리 정해 두고, take() 호출 횟수를 센다. */
    private static final class QueuedLines implements IncomingLines {
        private final Deque<List<List<Cell>>> batches = new ArrayDeque<>();
        int takes;

        QueuedLines(List<List<Cell>> first) {
            batches.add(first);
        }

        @Override
        public List<List<Cell>> take() {
            takes++;
            return batches.isEmpty() ? List.of() : batches.poll();
        }
    }

    private static PieceSource always(TetrominoType type) {
        return new PlainPieceSource(new PieceGenerator() {
            @Override
            public TetrominoType next() {
                return type;
            }

            @Override
            public TetrominoType peek() {
                return type;
            }
        });
    }

    /** 폭 width의 공격 줄: gap 열만 비어 있다. */
    private static List<Cell> attackLine(int width, int gap) {
        List<Cell> line = new ArrayList<>(Collections.nCopies(width, ATTACK));
        line.set(gap, EMPTY);
        return line;
    }

    private static List<List<Cell>> lines(int count, int width, int gap) {
        List<List<Cell>> result = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            result.add(attackLine(width, gap));
        }
        return result;
    }

    @Test
    void attackLinesArriveAfterTheClearAndBeforeTheNextPiece() {
        // 폭 4에서 가로 I는 한 줄을 꽉 채운다. 줄 삭제가 먼저 일어나야 바닥 줄이 지워진다.
        QueuedLines incoming = new QueuedLines(List.of(attackLine(4, 0), attackLine(4, 1)));
        PlayerEngine engine = new PlayerEngine(4, 6, always(TetrominoType.I), incoming);

        EngineStep step = engine.apply(GameAction.HARD_DROP);

        assertEquals(1, step.clearResult().lineCount());
        assertEquals(2, step.addedLines());
        Cell[][] board = step.snapshot().board();
        assertEquals(attackLine(4, 0), List.of(board[4]));
        assertEquals(attackLine(4, 1), List.of(board[5]));
        for (int y = 0; y < 4; y++) {
            for (Cell cell : board[y]) {
                assertTrue(cell.isEmpty(), "row " + y);
            }
        }
        assertEquals(EnginePhase.RUNNING, step.snapshot().phase());
        assertEquals(TetrominoType.I, step.snapshot().activePiece().shape());
    }

    @Test
    void attackLinesAreTakenOnlyWhenAPieceLocks() {
        QueuedLines incoming = new QueuedLines(List.of(attackLine(6, 0)));
        PlayerEngine engine = new PlayerEngine(6, 8, always(TetrominoType.T), incoming);

        engine.apply(GameAction.MOVE_LEFT);
        engine.apply(GameAction.ROTATE_CW);
        engine.apply(GameAction.SOFT_DROP);
        engine.tick();
        assertEquals(0, incoming.takes);

        EngineStep locked = engine.apply(GameAction.HARD_DROP);

        assertEquals(1, incoming.takes);
        assertEquals(1, locked.addedLines());
    }

    @Test
    void itemEffectsOnTheLockedPieceRunBeforeAttackLinesArrive() {
        // I의 0번 칸에 자기 줄을 지우는 아이템. 아이템이 공격 줄보다 먼저 발동해야 I가 놓인 줄이 지워지고
        // 들어온 공격 줄은 그대로 남는다.
        QueuedLines incoming = new QueuedLines(List.of(attackLine(5, 4)));
        PieceSource source = new PieceSource() {
            @Override
            public Piece next() {
                return Piece.of(TetrominoType.I).withItem(0, TestItems.clearsItsRow());
            }

            @Override
            public Piece peek() {
                return Piece.of(TetrominoType.I);
            }

            @Override
            public void onLinesCleared(int lines) {
            }
        };
        PlayerEngine engine = new PlayerEngine(5, 6, source, incoming);

        EngineStep step = engine.apply(GameAction.HARD_DROP);

        assertEquals(1, step.clearResult().lineCount());
        Cell[][] board = step.snapshot().board();
        assertEquals(attackLine(5, 4), List.of(board[5]));
        for (Cell cell : board[4]) {
            assertTrue(cell.isEmpty());
        }
    }

    @Test
    void cellsPushedAboveTheBoardEndTheGameWithoutSpawning() {
        // 폭 5에서 I는 줄을 다 채우지 못한다. 6줄이 들어오면 맨 아래의 I가 보드 위로 밀려 나간다.
        QueuedLines incoming = new QueuedLines(lines(6, 5, 4));
        PlayerEngine engine = new PlayerEngine(5, 6, always(TetrominoType.I), incoming);

        EngineStep step = engine.apply(GameAction.HARD_DROP);

        assertEquals(6, step.addedLines());
        assertEquals(EnginePhase.GAME_OVER, step.snapshot().phase());
        assertNull(step.snapshot().activePiece());
        assertEquals(attackLine(5, 4), List.of(step.snapshot().board()[0]));
        // 종료 뒤에는 공격 줄을 더 받지 않는다.
        engine.apply(GameAction.HARD_DROP);
        engine.tick();
        assertEquals(1, incoming.takes);
    }

    @Test
    void attackLinesThatBlockTheSpawnEndTheGameEvenWithoutOverflow() {
        // 5줄이 들어오면 I는 맨 위 줄까지 밀려 올라가지만 보드 안에 남는다. 그 아래는 공격 줄로 막혀
        // 다음 I를 놓을 수 없다.
        QueuedLines incoming = new QueuedLines(lines(5, 5, 4));
        PlayerEngine engine = new PlayerEngine(5, 6, always(TetrominoType.I), incoming);

        EngineStep step = engine.apply(GameAction.HARD_DROP);

        assertEquals(EnginePhase.GAME_OVER, step.snapshot().phase());
        Cell[][] board = step.snapshot().board();
        long pieceCellsOnTop = List.of(board[0]).stream().filter(c -> c.occupiedBy() == TetrominoType.I).count();
        assertEquals(4, pieceCellsOnTop);
    }

    @Test
    void singlePlayerEnginesNeverReceiveAttackLines() {
        PlayerEngine engine = new PlayerEngine(4, 6, always(TetrominoType.O));

        EngineStep step = engine.apply(GameAction.HARD_DROP);

        assertEquals(0, step.addedLines());
        assertEquals(EnginePhase.RUNNING, step.snapshot().phase());
        assertTrue(IncomingLines.NONE.take().isEmpty());
    }

    @Test
    void enginesStartingFromAScenarioAlsoReceiveAttackLines() {
        Cell[][] cells = new Cell[6][4];
        for (Cell[] row : cells) {
            java.util.Arrays.fill(row, EMPTY);
        }
        ActivePiece start = new ActivePiece(TetrominoType.O, 0, new Position(0, 0));
        QueuedLines incoming = new QueuedLines(List.of(attackLine(4, 3)));
        PlayerEngine engine = new PlayerEngine(cells, start, always(TetrominoType.O), incoming);

        EngineStep step = engine.apply(GameAction.HARD_DROP);

        assertEquals(1, step.addedLines());
        assertEquals(attackLine(4, 3), List.of(step.snapshot().board()[5]));
    }

    @Test
    void anOverflowingStepCarriesTheRowsPushedAboveTheBoard() {
        // 폭 5, 높이 6에서 맨 위에 T 칸이 있다. 공격 줄 1줄이 들어오면 그 줄이 밀려 나간다.
        Cell[][] cells = new Cell[6][5];
        for (Cell[] row : cells) {
            java.util.Arrays.fill(row, EMPTY);
        }
        cells[0][4] = Cell.occupiedBy(TetrominoType.T);
        List<Cell> topRow = List.of(cells[0].clone());
        ActivePiece start = new ActivePiece(TetrominoType.O, 0, new Position(0, 0));
        QueuedLines incoming = new QueuedLines(List.of(attackLine(5, 2)));
        PlayerEngine engine = new PlayerEngine(cells, start, always(TetrominoType.O), incoming);

        EngineStep step = engine.apply(GameAction.HARD_DROP);

        assertEquals(EnginePhase.GAME_OVER, step.snapshot().phase());
        assertEquals(List.of(topRow), step.pushResult().pushedOutRows());
        assertTrue(step.pushResult().overflowed());
    }

    @Test
    void aSupplierReturningNullIsRejected() {
        PlayerEngine engine = new PlayerEngine(4, 6, always(TetrominoType.O), () -> null);

        org.junit.jupiter.api.Assertions.assertThrows(NullPointerException.class,
                () -> engine.apply(GameAction.HARD_DROP));
    }

    @Test
    void stepsWithoutAttackLinesHaveAnEmptyPushResult() {
        PlayerEngine engine = new PlayerEngine(4, 6, always(TetrominoType.O));

        EngineStep step = new EngineStep(engine.snapshot(), null, null, null, List.of(), List.of(), null);

        assertEquals(0, step.addedLines());
        assertTrue(step.pushResult().pushedOutRows().isEmpty());
    }
}
