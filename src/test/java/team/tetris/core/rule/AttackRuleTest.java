package team.tetris.core.rule;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;
import team.tetris.core.ActivePiece;
import team.tetris.core.AttackBlock;
import team.tetris.core.Board;
import team.tetris.core.Cell;
import team.tetris.core.GameAction;
import team.tetris.core.Piece;
import team.tetris.core.PlayerEngine;
import team.tetris.core.Position;
import team.tetris.core.TetrominoType;
import team.tetris.core.item.BombItem;
import team.tetris.core.item.LineClearItem;
import team.tetris.core.item.WeightBlock;
import team.tetris.core.result.ClearResult;
import team.tetris.core.result.ClearedRow;
import team.tetris.core.result.EngineStep;
import team.tetris.core.result.LockResult;

/** 공격 줄 모양 계산 (3차 R3-06, 안건 ⑭·⑮·㉔). */
class AttackRuleTest {

    private static final Cell A = Cell.occupiedBy(AttackBlock.INSTANCE);
    private static final Cell E = Cell.EMPTY;
    private static final int WIDTH = 10;
    private static final int HEIGHT = 8;

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

    /** 폭 10의 줄: holes 열만 비우고 나머지는 shape 칸. */
    private static Cell[] row(TetrominoType shape, int... holes) {
        Cell[] row = new Cell[WIDTH];
        Arrays.fill(row, Cell.occupiedBy(shape));
        for (int hole : holes) {
            row[hole] = E;
        }
        return row;
    }

    private static Cell[][] emptyBoard() {
        return new Board(WIDTH, HEIGHT).snapshot();
    }

    /** 공격 줄: holes 열만 비고 나머지는 공격 칸. */
    private static List<Cell> attack(int... holes) {
        List<Cell> line = new ArrayList<>(Collections.nCopies(WIDTH, A));
        for (int hole : holes) {
            line.set(hole, E);
        }
        return line;
    }

    private static EngineStep hardDrop(Cell[][] board, ActivePiece piece) {
        return new PlayerEngine(board, piece, always(TetrominoType.O)).apply(GameAction.HARD_DROP);
    }

    @Test
    void anLPieceClearingThreeLinesSendsThoseLinesWithoutItsOwnCells() {
        // 3차 p4 예제: L자 블록으로 3줄을 지우면 그 3줄에서 L 칸을 뺀 모양이 넘어간다.
        // 갈고리가 위에 있는 L(회전 1)은 (3,5)(4,5)(4,6)(4,7)에 놓인다.
        Cell[][] board = emptyBoard();
        board[5] = row(TetrominoType.O, 3, 4);
        board[6] = row(TetrominoType.T, 4);
        board[6][8] = Cell.withItem(TetrominoType.T, new LineClearItem()); // 보드에 남은 아이템 (안건 ㉔)
        board[7] = row(TetrominoType.Z, 4);

        EngineStep step = hardDrop(board, new ActivePiece(TetrominoType.L, 1, new Position(3, 0)));

        assertEquals(3, step.clearResult().lineCount());
        assertEquals(List.of(attack(3, 4), attack(4), attack(4)),
                AttackRule.linesFor(step.lockResult(), step.clearResult()));
    }

    @Test
    void clearingASingleLineDoesNotAttack() {
        Cell[][] board = emptyBoard();
        board[7] = row(TetrominoType.O, 3, 4, 5, 6);

        EngineStep step = hardDrop(board, new ActivePiece(TetrominoType.I, 0, new Position(3, 0)));

        assertEquals(1, step.clearResult().lineCount());
        assertTrue(AttackRule.linesFor(step.lockResult(), step.clearResult()).isEmpty());
    }

    @Test
    void stepsWithoutALockOrAClearDoNotAttack() {
        LockResult lock = new LockResult(TetrominoType.I, new Position(0, 0), 0);

        assertTrue(AttackRule.linesFor(null, null).isEmpty());
        assertTrue(AttackRule.linesFor(lock, null).isEmpty());
        assertTrue(AttackRule.linesFor(lock, ClearResult.NONE).isEmpty());
    }

    @Test
    void aRowClearedByTheLineClearItemCountsTowardsTheAttack() {
        // 안건 ⑭: 줄 삭제 아이템(L)으로 지운 줄도 줄 단위 삭제라 공격이 된다. L(회전 1)의 갈고리 칸(3,5)에
        // 아이템을 실어 꽉 차지 않은 5번 줄을 지우고, 7번 줄은 꽉 차서 지워진다. 6번 줄은 남는다.
        Cell[][] board = emptyBoard();
        board[5] = row(TetrominoType.O, 0, 1, 3, 4);
        board[6] = row(TetrominoType.T, 4, 9);
        board[7] = row(TetrominoType.Z, 4);
        Piece piece = Piece.of(TetrominoType.L).withItem(0, new LineClearItem());

        EngineStep step = hardDrop(board, new ActivePiece(piece, 1, new Position(3, 0)));

        assertEquals(List.of(5, 7), step.clearResult().clearedRows().stream().map(ClearedRow::rowIndex).toList());
        assertEquals(List.of(attack(0, 1, 3, 4), attack(4)),
                AttackRule.linesFor(step.lockResult(), step.clearResult()));
    }

    @Test
    void cellsRemovedByABombDoNotAttack() {
        // 안건 ⑭: 폭탄·연쇄 삭제처럼 칸 단위로 지운 경우는 공격하지 않는다. 폭탄이 두 줄에 걸쳐 칸을
        // 지우지만 지워진 줄은 없다.
        Cell[][] board = emptyBoard();
        board[6] = row(TetrominoType.O, 3, 4, 5, 6, 9);
        board[7] = row(TetrominoType.T, 3, 4, 5, 6);
        Piece piece = Piece.of(TetrominoType.I).withItem(0, new BombItem());

        EngineStep step = hardDrop(board, new ActivePiece(piece, 0, new Position(3, 0)));

        assertTrue(step.removedCells().size() > 2);
        assertTrue(step.clearResult().isEmpty());
        assertTrue(AttackRule.linesFor(step.lockResult(), step.clearResult()).isEmpty());
    }

    @Test
    void aWeightEmptiesItsFinalCellsInTheAttack() {
        // 안건 ⑮: 무게추로 2줄 이상 지우면 지운 줄에서 무게추의 최종 고정 칸을 비운 모양으로 공격한다.
        // 무게추(회전 0)는 origin (2,6)에서 (3,6)(4,6)과 (2,7)(3,7)(4,7)(5,7)을 차지한다.
        // 실제 게임에서는 무게추가 내려오며 바깥 두 열의 칸을 지워 윗줄이 꽉 찰 수 없으므로 한 번에 1줄까지만
        // 지운다(무작위 4만 판에서 확인). 규칙이 바뀌어도 맞게 동작하도록 고정 결과를 직접 만들어 확인한다.
        LockResult lock = new LockResult(WeightBlock.SHAPE, new Position(2, 6), 0);
        ClearResult clear = new ClearResult(List.of(
                new ClearedRow(6, List.of(row(TetrominoType.O))),
                new ClearedRow(7, List.of(row(TetrominoType.S)))));

        assertEquals(List.of(attack(3, 4), attack(2, 3, 4, 5)), AttackRule.linesFor(lock, clear));
    }

    @Test
    void theRowOrderOfTheClearIsKept() {
        // 떨어진 두 줄(2번, 6번)을 지워도 위쪽 줄이 먼저 온다.
        LockResult lock = new LockResult(TetrominoType.I, new Position(0, 0), 1);
        ClearResult clear = new ClearResult(List.of(
                new ClearedRow(2, List.of(row(TetrominoType.O, 7))),
                new ClearedRow(6, List.of(row(TetrominoType.O, 1)))));

        List<List<Cell>> lines = AttackRule.linesFor(lock, clear);

        // 세운 I(회전 1)의 칸은 (2,0)~(2,3)이라 2번 줄의 2열이 함께 빈다.
        assertEquals(List.of(attack(2, 7), attack(1)), lines);
    }

    @Test
    void aClearedRowWithoutAnyGapIsNotSent() {
        // 꽉 찬 채로 시작한 보드처럼 지운 줄에 마지막 블록 칸이 없으면, 받은 쪽에서 저절로 지워지므로
        // 그 줄은 보내지 않는다.
        LockResult lock = new LockResult(TetrominoType.O, new Position(0, 0), 0);
        ClearResult clear = new ClearResult(List.of(
                new ClearedRow(0, List.of(row(TetrominoType.T, 9))),
                new ClearedRow(7, List.of(row(TetrominoType.T)))));

        // O(origin (0,0))는 (1,0)(2,0)(1,1)(2,1)을 차지하므로 0번 줄의 1·2열이 빈다.
        assertEquals(List.of(attack(1, 2, 9)), AttackRule.linesFor(lock, clear));
    }

    @Test
    void attackLinesCarryNoItemsAndOnlyAttackCells() {
        LockResult lock = new LockResult(TetrominoType.O, new Position(7, 0), 0);
        Cell[] withItems = row(TetrominoType.J);
        withItems[0] = Cell.withItem(TetrominoType.J, new LineClearItem());
        ClearResult clear = new ClearResult(List.of(
                new ClearedRow(0, List.of(withItems)), new ClearedRow(1, List.of(withItems))));

        for (List<Cell> line : AttackRule.linesFor(lock, clear)) {
            for (Cell cell : line) {
                assertTrue(cell.isEmpty() || cell.equals(A), cell::toString);
            }
        }
    }
}
