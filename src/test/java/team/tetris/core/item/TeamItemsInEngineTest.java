package team.tetris.core.item;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import team.tetris.core.Cell;
import team.tetris.core.GameAction;
import team.tetris.core.Piece;
import team.tetris.core.PlayerEngine;
import team.tetris.core.Position;
import team.tetris.core.TetrominoType;
import team.tetris.core.result.EngineStep;
import team.tetris.core.rule.PieceSource;

/** 실제 엔진의 고정 처리 안에서 팀 아이템이 발동하는지 확인한다. */
class TeamItemsInEngineTest {

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

    private static int filled(Cell[][] board) {
        int count = 0;
        for (Cell[] row : board) {
            for (Cell cell : row) {
                if (!cell.isEmpty()) {
                    count++;
                }
            }
        }
        return count;
    }

    @Test
    void aBombBlockFallsWithoutRotatingAndBlowsUpAroundWhereItLands() {
        BombItem bomb = new BombItem();
        // 폭 4: O는 x=1~2에 스폰, 1칸 폭탄은 x=1에 스폰된다.
        PlayerEngine engine = new PlayerEngine(4, 6,
                sequence(Piece.of(TetrominoType.O), new SoloBlock(bomb).toItemPiece(null, null)));
        engine.apply(GameAction.HARD_DROP); // O가 (1~2, 4~5)에 고정

        engine.apply(GameAction.ROTATE_CW);
        assertSame(SoloBlock.SHAPE, engine.snapshot().activePiece().shape());
        assertEquals(0, engine.snapshot().activePiece().rotation());

        EngineStep step = engine.apply(GameAction.HARD_DROP); // 폭탄이 (1, 3)에 고정 → (0~2, 2~4) 삭제

        assertEquals(1, step.itemActivations().size());
        assertSame(bomb, step.itemActivations().get(0).item());
        assertEquals(new Position(1, 3), step.itemActivations().get(0).position());
        Cell[][] board = step.snapshot().board();
        assertTrue(board[3][1].isEmpty());
        assertTrue(board[4][1].isEmpty());
        assertTrue(board[4][2].isEmpty());
        assertEquals(TetrominoType.O, board[5][1].occupiedBy());
        assertEquals(TetrominoType.O, board[5][2].occupiedBy());
        assertEquals(2, filled(board));
    }

    @Test
    void aChainClearBlockRemovesTheTouchingBlocksOfItsKind() {
        Piece chain = new Piece(TetrominoType.O, Map.of(0, new ChainClearItem()));
        // 폭 6: O는 x=2~3에 스폰. 첫 O를 왼쪽 끝으로, 아이템 O는 바로 오른쪽에 붙인다.
        PlayerEngine engine = new PlayerEngine(6, 6, sequence(Piece.of(TetrominoType.O), chain));
        engine.apply(GameAction.MOVE_LEFT);
        engine.apply(GameAction.MOVE_LEFT);
        engine.apply(GameAction.HARD_DROP); // (0~1, 4~5)

        EngineStep step = engine.apply(GameAction.HARD_DROP); // (2~3, 4~5)

        assertEquals(1, step.itemActivations().size());
        assertEquals(0, filled(step.snapshot().board()));
    }

    @Test
    void aDoubleScoreBlockLeavesThePlainCellOnTheBoard() {
        Piece doubled = new Piece(TetrominoType.O, Map.of(2, new DoubleScoreItem()));
        PlayerEngine engine = new PlayerEngine(6, 6, sequence(doubled));

        EngineStep step = engine.apply(GameAction.HARD_DROP);

        assertEquals(1, step.itemActivations().size());
        assertTrue(step.itemActivations().get(0).item() instanceof DoubleScoreItem);
        Cell[][] board = step.snapshot().board();
        assertEquals(4, filled(board));
        for (Cell[] row : board) {
            for (Cell cell : row) {
                assertTrue(!cell.hasItem());
            }
        }
    }
}
