package team.tetris.core.rule;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
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
import team.tetris.core.item.ChainClearItem;
import team.tetris.core.item.DoubleScoreItem;
import team.tetris.core.item.Item;
import team.tetris.core.item.LineClearItem;
import team.tetris.core.item.SoloBlock;
import team.tetris.core.item.WeightBlock;
import team.tetris.core.result.ClearedRow;
import team.tetris.core.result.EngineStep;

/**
 * 아이템별 공격 여부와 공격 줄 모양을 실제 엔진으로 확인한다 (3차 카드 21, 안건 ⑭·⑮·㉔).
 *
 * <p>공격 규칙 자체는 {@link AttackRuleTest}(카드 16)가 다룬다. 여기서는 아이템 효과가 끝난 뒤 엔진이
 * 보고한 줄 삭제 결과로 공격이 정해지는지, 아이템마다 확인한다.
 */
class ItemAttackTest {

    private static final Cell A = Cell.occupiedBy(AttackBlock.INSTANCE);
    private static final Cell E = Cell.EMPTY;
    private static final int WIDTH = 10;
    private static final int HEIGHT = 8;
    /** 보드 가운데(4·5열)로 떨어지는 O의 시작 위치. */
    private static final Position O_START = new Position(3, 0);

    private static PieceSource alwaysO() {
        return new PlainPieceSource(new PieceGenerator() {
            @Override
            public TetrominoType next() {
                return TetrominoType.O;
            }

            @Override
            public TetrominoType peek() {
                return TetrominoType.O;
            }
        });
    }

    private static Cell[][] emptyBoard() {
        return new Board(WIDTH, HEIGHT).snapshot();
    }

    /** holes 열만 비고 나머지는 shape 칸인 줄. */
    private static Cell[] row(TetrominoType shape, int... holes) {
        Cell[] row = new Cell[WIDTH];
        Arrays.fill(row, Cell.occupiedBy(shape));
        for (int hole : holes) {
            row[hole] = E;
        }
        return row;
    }

    /** holes 열만 빈 공격 줄. */
    private static List<Cell> attack(int... holes) {
        List<Cell> line = new ArrayList<>(Collections.nCopies(WIDTH, A));
        for (int hole : holes) {
            line.set(hole, E);
        }
        return line;
    }

    /** 맨 아래 두 줄이 4·5열만 빈 보드. O가 떨어지면 두 줄이 꽉 찬다. */
    private static Cell[][] twoLinesOpenAt45(TetrominoType filler) {
        Cell[][] board = emptyBoard();
        board[HEIGHT - 2] = row(filler, 4, 5);
        board[HEIGHT - 1] = row(filler, 4, 5);
        return board;
    }

    /** O의 칸 가운데 윗줄(upper)이나 아랫줄 칸 하나에 item을 실은 블록. */
    static Piece oWithItem(Item item, boolean upper) {
        Position[] cells = TetrominoType.O.cellsAt(0);
        int target = upper ? Integer.MAX_VALUE : Integer.MIN_VALUE;
        int index = -1;
        for (int i = 0; i < cells.length; i++) {
            if (upper ? cells[i].y() < target : cells[i].y() > target) {
                target = cells[i].y();
                index = i;
            }
        }
        return Piece.of(TetrominoType.O).withItem(index, item);
    }

    private static EngineStep hardDrop(Cell[][] board, Piece piece, Position start) {
        return new PlayerEngine(board, new ActivePiece(piece, 0, start), alwaysO()).apply(GameAction.HARD_DROP);
    }

    private static List<List<Cell>> attackOf(EngineStep step) {
        return AttackRule.linesFor(step.lockResult(), step.clearResult());
    }

    @Test
    void aChainClearThatBreaksTheFullRowsLeavesNothingToAttackWith() {
        // 안건 ⑭: 연쇄 삭제는 칸 단위 삭제라 공격하지 않는다. O가 두 줄을 채우지만 연쇄 삭제가 이어진 O 칸
        // 덩어리(두 줄 전체)를 먼저 지우므로 꽉 찬 줄이 남지 않는다.
        EngineStep step = hardDrop(twoLinesOpenAt45(TetrominoType.O), oWithItem(new ChainClearItem(), false), O_START);

        assertTrue(step.clearResult().isEmpty());
        assertEquals(2 * WIDTH, step.removedCells().size());
        assertTrue(attackOf(step).isEmpty());
        for (Cell cell : step.snapshot().board()[HEIGHT - 1]) {
            assertTrue(cell.isEmpty());
        }
    }

    @Test
    void aChainClearWithoutMatchingNeighboursLetsTheFullRowsAttackAsUsual() {
        // 이웃에 같은 종류(O) 칸이 없으면 연쇄 삭제는 아무것도 지우지 않고, 꽉 찬 두 줄은 평소처럼 공격이 된다.
        EngineStep step = hardDrop(twoLinesOpenAt45(TetrominoType.T), oWithItem(new ChainClearItem(), false), O_START);

        assertTrue(step.removedCells().isEmpty());
        assertEquals(2, step.clearResult().lineCount());
        assertEquals(List.of(attack(4, 5), attack(4, 5)), attackOf(step));
    }

    @Test
    void theDoubleScoreItemAttacksExactlyLikeAPlainPiece() {
        // 점수 2배($)는 점수에만 영향이 있다. 같은 자리에서 일반 O와 같은 공격 줄이 나온다.
        EngineStep withItem = hardDrop(twoLinesOpenAt45(TetrominoType.T), oWithItem(new DoubleScoreItem(), true), O_START);
        EngineStep plain = hardDrop(twoLinesOpenAt45(TetrominoType.T), Piece.of(TetrominoType.O), O_START);

        assertEquals(1, withItem.itemActivations().size());
        assertEquals(attackOf(plain), attackOf(withItem));
        assertEquals(List.of(attack(4, 5), attack(4, 5)), attackOf(withItem));
    }

    @Test
    void aLineClearOnARowHoldingOnlyThePieceSendsAnEmptyLine() {
        // 안건 ⑭: L로 지운 줄도 공격이 되며, 아이템 효과 뒤 남은 빈칸을 그대로 보낸다. 지운 줄에 마지막 블록의
        // 칸만 있었다면 그 칸을 비운 결과는 빈 줄이다. 받은 쪽에는 빈 줄이 그대로 들어가 보드가 한 줄 올라간다.
        Cell[][] board = emptyBoard();
        board[HEIGHT - 1] = row(TetrominoType.T, 4, 5);

        EngineStep step = hardDrop(board, oWithItem(new LineClearItem(), true), O_START);

        assertEquals(List.of(HEIGHT - 2, HEIGHT - 1),
                step.clearResult().clearedRows().stream().map(ClearedRow::rowIndex).toList());
        List<List<Cell>> lines = attackOf(step);
        assertEquals(List.of(attack(0, 1, 2, 3, 4, 5, 6, 7, 8, 9), attack(4, 5)), lines);

        // 받는 쪽: 대기열과 보드 아래 줄 추가가 빈 줄을 받아들이고 순서를 지킨다.
        AttackQueue queue = new AttackQueue(WIDTH);
        queue.add(lines);
        PlayerEngine receiver = new PlayerEngine(emptyBoard(), new ActivePiece(TetrominoType.O, 0, new Position(0, 0)),
                alwaysO(), queue);
        receiver.apply(GameAction.HARD_DROP);
        Cell[][] after = receiver.snapshot().board();
        assertEquals(attack(4, 5), Arrays.asList(after[HEIGHT - 1]));
        assertEquals(attack(0, 1, 2, 3, 4, 5, 6, 7, 8, 9), Arrays.asList(after[HEIGHT - 2]));
        assertFalse(after[HEIGHT - 3][1].isEmpty(), "받은 쪽의 O는 두 줄 위로 밀려 올라간다");
    }

    @Test
    void aBombThatFillsTheLastGapStillClearsNoLine() {
        // 폭탄 칸이 줄의 마지막 빈칸을 채워도, 곧바로 주변 3×3을 지우므로 꽉 찬 줄이 남지 않아 공격이 없다.
        Cell[][] board = emptyBoard();
        board[HEIGHT - 1] = row(TetrominoType.T, 4);
        Piece bomb = new SoloBlock(new BombItem()).toItemPiece(Piece.of(TetrominoType.O), null);

        EngineStep step = hardDrop(board, bomb, new Position(4, 0));

        assertTrue(step.clearResult().isEmpty());
        assertFalse(step.removedCells().isEmpty());
        assertTrue(attackOf(step).isEmpty());
    }

    @Test
    void cellsAWeightCrushesOnTheWayDownDoNotAttack() {
        // 안건 ⑭·⑮: 무게추가 내려오며 지운 칸은 칸 단위 삭제다. 고정으로 한 줄을 지워도 2줄이 아니면 공격이 없다.
        Cell[][] board = emptyBoard();
        board[HEIGHT - 3] = row(TetrominoType.S, 0, 1, 7, 8, 9);
        board[HEIGHT - 1] = row(TetrominoType.Z, 3, 4, 5, 6);
        Piece weight = new Piece(WeightBlock.SHAPE, Map.of(0, new WeightBlock()));

        EngineStep step = hardDrop(board, weight, new Position(3, 0));

        assertFalse(step.removedCells().isEmpty(), "지나가며 지운 칸");
        assertEquals(1, step.clearResult().lineCount(), "무게추 아래 4칸이 맨 아래 줄을 채운다");
        assertTrue(attackOf(step).isEmpty());
    }

    @Test
    void noItemSurvivesOnTheBoardAfterALockSoAttackLinesNeverCarryOne() {
        // 안건 ㉔: 아이템은 고정할 때 한 번 발동하고 표시가 지워진다. 그래서 지운 줄에도, 공격 줄에도 아이템이 없다.
        for (Item item : List.of(new LineClearItem(), new DoubleScoreItem(), new ChainClearItem())) {
            EngineStep step = hardDrop(twoLinesOpenAt45(TetrominoType.T), oWithItem(item, true), O_START);

            for (ClearedRow cleared : step.clearResult().clearedRows()) {
                for (Cell cell : cleared.cells()) {
                    assertEquals(null, cell.item(), item::toString);
                }
            }
            for (List<Cell> line : attackOf(step)) {
                for (Cell cell : line) {
                    assertTrue(cell.isEmpty() || cell.equals(A), cell::toString);
                }
            }
        }
    }
}
