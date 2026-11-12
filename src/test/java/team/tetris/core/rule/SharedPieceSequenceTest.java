package team.tetris.core.rule;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;
import team.tetris.core.TetrominoType;

/** 두 플레이어의 기본 블록 순서 공유 (3차 안건 ⑰). */
class SharedPieceSequenceTest {

    /** 뽑은 순서를 기록하는 무작위 생성기. */
    private static final class Recording implements PieceGenerator {
        private final Random random = new Random(7);
        private final List<TetrominoType> drawn = new ArrayList<>();
        private TetrominoType next = pick();

        private TetrominoType pick() {
            return TetrominoType.values()[random.nextInt(TetrominoType.values().length)];
        }

        @Override
        public TetrominoType next() {
            TetrominoType current = next;
            drawn.add(current);
            next = pick();
            return current;
        }

        @Override
        public TetrominoType peek() {
            return next;
        }
    }

    @Test
    void everyReaderGetsTheSameOrderWhateverItsPace() {
        Recording source = new Recording();
        SharedPieceSequence sequence = new SharedPieceSequence(source);
        PieceGenerator fast = sequence.newReader();
        PieceGenerator slow = sequence.newReader();

        List<TetrominoType> fastPieces = new ArrayList<>();
        List<TetrominoType> slowPieces = new ArrayList<>();
        for (int i = 0; i < 300; i++) {
            fastPieces.add(fast.next());
            if (i % 3 == 0) {
                slowPieces.add(slow.next());
            }
        }
        while (slowPieces.size() < fastPieces.size()) {
            assertEquals(fastPieces.get(slowPieces.size()), slow.peek());
            slowPieces.add(slow.next());
        }

        assertEquals(fastPieces, slowPieces);
        assertEquals(source.drawn, fastPieces);
    }

    @Test
    void peekShowsTheNextPieceOfThatReaderOnly() {
        SharedPieceSequence sequence = new SharedPieceSequence(new Recording());
        PieceGenerator one = sequence.newReader();
        PieceGenerator two = sequence.newReader();

        TetrominoType first = two.peek();
        assertEquals(first, one.next());
        TetrominoType second = one.peek();

        assertEquals(first, two.peek());
        assertEquals(first, two.next());
        assertEquals(second, two.next());
        assertEquals(second, one.next());
    }

    @Test
    void readersMustAllJoinBeforeTheGameStarts() {
        SharedPieceSequence sequence = new SharedPieceSequence(new Recording());
        PieceGenerator only = sequence.newReader();
        only.next();

        assertThrows(IllegalStateException.class, sequence::newReader);
    }
}
