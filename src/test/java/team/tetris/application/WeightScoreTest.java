package team.tetris.application;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Random;
import org.junit.jupiter.api.Test;
import team.tetris.core.Piece;
import team.tetris.core.PlayerEngine;
import team.tetris.core.TetrominoType;
import team.tetris.core.item.WeightBlock;
import team.tetris.core.rule.PieceSource;

/** 무게추의 낙하와 무게추가 지운 블록에는 점수가 없다 (2차 요구사항 p9). */
class WeightScoreTest {

    private static PieceSource weightThenO() {
        Piece weight = new WeightBlock().toItemPiece(Piece.of(TetrominoType.I), new Random(1));
        return new PieceSource() {
            private boolean used;

            @Override
            public Piece next() {
                Piece next = used ? Piece.of(TetrominoType.O) : weight;
                used = true;
                return next;
            }

            @Override
            public Piece peek() {
                return used ? Piece.of(TetrominoType.O) : weight;
            }
        };
    }

    @Test
    void droppingTheWeightGivesNoPoints() {
        SinglePlayerSession session = new SinglePlayerSession(new PlayerEngine(10, 20, weightThenO()));

        session.update(3_000_000_000L); // 자동 낙하 3칸
        session.handle(GameCommand.SOFT_DROP);
        session.handle(GameCommand.HARD_DROP);

        assertEquals(0, session.snapshot().score());
        assertEquals(0, session.snapshot().clearedLines());
    }
}
