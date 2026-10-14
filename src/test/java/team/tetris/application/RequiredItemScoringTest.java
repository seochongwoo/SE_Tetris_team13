package team.tetris.application;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import org.junit.jupiter.api.Test;
import team.tetris.core.Piece;
import team.tetris.core.PlayerEngine;
import team.tetris.core.TetrominoType;
import team.tetris.core.item.DoubleScoreItem;
import team.tetris.core.item.LineClearItem;
import team.tetris.core.item.WeightBlock;
import team.tetris.core.rule.PieceSource;

class RequiredItemScoringTest {
    @Test
    void lineClearScoresAndCountsLikeANormalLineIncludingTheDoubleScoreBonus() {
        Piece line = Piece.of(TetrominoType.O).withItem(2, new LineClearItem());
        SinglePlayerSession session = session(6, 10, line.withItem(0, new DoubleScoreItem()), line);
        session.handle(GameCommand.HARD_DROP);
        assertEquals(216, session.snapshot().score()); // (8칸 하강 + 100) × 2
        assertEquals(1, session.snapshot().clearedLines());
        assertEquals(2, session.snapshot().doubleScoreClears());
        session.handle(GameCommand.HARD_DROP);
        assertEquals(430, session.snapshot().score()); // (7칸 하강 + 100) × 2
        assertEquals(2, session.snapshot().clearedLines());
    }

    @Test
    void weightEarnsNoDropOrCrushPointsAndTheFollowingNormalPieceStillDoes() {
        Piece weight = new WeightBlock().toItemPiece(null, null);
        SinglePlayerSession session = session(8, 10, Piece.of(TetrominoType.O), weight);
        session.handle(GameCommand.HARD_DROP);
        long before = session.snapshot().score();
        assertEquals(8, before); // 일반 블록 다음에 W가 스폰돼도 일반 하강은 점수 획득
        session.handle(GameCommand.SOFT_DROP);
        session.update(session.snapshot().gravityIntervalNanos());
        session.handle(GameCommand.HARD_DROP);
        assertEquals(before, session.snapshot().score());
        assertEquals(0, session.snapshot().clearedLines());
        session.handle(GameCommand.SOFT_DROP);
        assertEquals(before + 1, session.snapshot().score());
    }

    @Test
    void weightGravityAndSoftDropRemainFreeEvenOnTheLockingStep() {
        for (boolean gravity : List.of(false, true)) {
            SinglePlayerSession session = session(8, 10, new WeightBlock().toItemPiece(null, null));
            for (int i = 0; i < 9; i++) {
                if (gravity) session.update(session.snapshot().gravityIntervalNanos());
                else session.handle(GameCommand.SOFT_DROP);
                assertEquals(0, session.snapshot().score());
            }
            assertEquals(TetrominoType.O, session.snapshot().engine().activePiece().shape());
        }
    }

    @Test
    void aFullRowCompletedByTheWeightStillUsesNormalLineScoring() {
        SinglePlayerSession session = session(4, 10, new WeightBlock().toItemPiece(null, null));
        session.handle(GameCommand.HARD_DROP);
        assertEquals(100, session.snapshot().score());
        assertEquals(1, session.snapshot().clearedLines());
    }

    private static SinglePlayerSession session(int width, int height, Piece... pieces) {
        Deque<Piece> queue = new ArrayDeque<>(List.of(pieces));
        PieceSource source = new PieceSource() {
            public Piece next() {
                return queue.isEmpty() ? Piece.of(TetrominoType.O) : queue.remove();
            }
            public Piece peek() {
                return queue.isEmpty() ? Piece.of(TetrominoType.O) : queue.peek();
            }
        };
        return new SinglePlayerSession(new PlayerEngine(width, height, source));
    }
}
