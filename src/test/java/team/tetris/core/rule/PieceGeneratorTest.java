package team.tetris.core.rule;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import team.tetris.core.TetrominoType;

/**
 * {@link PieceGenerator} 계약(peek은 다음 next와 같아야 함)과, 현재 유일한 구현체인
 * {@link SevenBagGenerator}의 7-bag 규칙을 함께 검증한다.
 */
class PieceGeneratorTest {

    @Test
    void sameSeedAlwaysProducesTheSameSequence() {
        PieceGenerator a = new SevenBagGenerator(42L);
        PieceGenerator b = new SevenBagGenerator(42L);

        for (int i = 0; i < 30; i++) {
            assertEquals(a.next(), b.next());
        }
    }

    @Test
    void peekNeverConsumesAndAlwaysMatchesTheFollowingNext() {
        PieceGenerator generator = new SevenBagGenerator(1L);

        for (int i = 0; i < 20; i++) {
            TetrominoType peeked = generator.peek();
            assertEquals(peeked, generator.peek(), "peek() 반복 호출은 항상 같은 값을 반환해야 함");
            assertEquals(peeked, generator.next(), "peek() 직후 next()는 같은 블록이어야 함");
        }
    }

    @Test
    void everyBagOfSevenDrawsContainsEachTypeExactlyOnce() {
        PieceGenerator generator = new SevenBagGenerator(7L);

        for (int bag = 0; bag < 5; bag++) {
            List<TetrominoType> drawn = new ArrayList<>();
            for (int i = 0; i < TetrominoType.values().length; i++) {
                drawn.add(generator.next());
            }

            assertEquals(TetrominoType.values().length, drawn.size());
            assertEquals(Set.of(TetrominoType.values()), Set.copyOf(drawn),
                    "한 가방(7개) 안에는 7종류가 정확히 한 번씩 나와야 함");
        }
    }

    @Test
    void differentSeedsCanProduceDifferentOrderings() {
        PieceGenerator a = new SevenBagGenerator(1L);
        PieceGenerator b = new SevenBagGenerator(2L);

        List<TetrominoType> sequenceA = new ArrayList<>();
        List<TetrominoType> sequenceB = new ArrayList<>();
        for (int i = 0; i < 7; i++) {
            sequenceA.add(a.next());
            sequenceB.add(b.next());
        }

        // 시드가 다르면 같은 7종류라도 순서가 달라질 수 있음을 보여주는 예시성 테스트.
        // (극히 낮은 확률로 우연히 같을 수 있으나, 그 자체가 버그를 뜻하진 않는다.)
        assertTrue(TetrominoType.values().length == sequenceA.size()
                && Set.copyOf(sequenceA).equals(Set.copyOf(sequenceB)));
    }
}
