package team.tetris.core.rule;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import org.junit.jupiter.api.Test;
import team.tetris.core.TetrominoType;

/**
 * 요구사항 2(확률에 따른 블럭 생성): 최소 1,000번 이상 뽑아 설정한 확률과 큰 차이 없이(오차 ±5% 이내)
 * 분포가 나타나는지 확인한다.
 */
class WeightedRandomGeneratorTest {

    private static Map<TetrominoType, Integer> count(PieceGenerator generator, int draws) {
        Map<TetrominoType, Integer> counts = new EnumMap<>(TetrominoType.class);
        for (int i = 0; i < draws; i++) {
            counts.merge(generator.next(), 1, Integer::sum);
        }
        return counts;
    }

    @Test
    void theRequirementExampleGivesFortyAndTwentyPercentOverAThousandDraws() {
        // 요구사항 예시: 블록 4개 중 B의 선택 확률이 다른 것의 2배면 B는 40%, 나머지는 20%.
        Map<TetrominoType, Integer> weights = Map.of(
                TetrominoType.I, 2, TetrominoType.O, 1, TetrominoType.T, 1, TetrominoType.S, 1);
        int draws = 1_000;

        Map<TetrominoType, Integer> counts = count(new WeightedRandomGenerator(weights, new Random(2026L)), draws);

        assertEquals(400, counts.get(TetrominoType.I), 50); // 40% ± 5%p
        for (TetrominoType type : new TetrominoType[] {TetrominoType.O, TetrominoType.T, TetrominoType.S}) {
            assertEquals(200, counts.get(type), 50, type.name()); // 20% ± 5%p
        }
        assertEquals(draws, counts.values().stream().mapToInt(Integer::intValue).sum());
    }

    @Test
    void withManyDrawsEveryShareIsWithinFivePercentOfItsExpectedValue() {
        Map<TetrominoType, Integer> weights = Map.of(
                TetrominoType.I, 2, TetrominoType.O, 1, TetrominoType.T, 1, TetrominoType.S, 1);
        int draws = 100_000;

        Map<TetrominoType, Integer> counts = count(new WeightedRandomGenerator(weights, new Random(7L)), draws);

        for (Map.Entry<TetrominoType, Integer> entry : weights.entrySet()) {
            double expected = draws * entry.getValue() / 5.0;
            assertEquals(expected, counts.get(entry.getKey()), expected * 0.05, entry.getKey().name());
        }
    }

    @Test
    void piecesWithZeroWeightNeverAppear() {
        Map<TetrominoType, Integer> weights = new EnumMap<>(TetrominoType.class);
        weights.put(TetrominoType.I, 0);
        weights.put(TetrominoType.T, 3);

        Map<TetrominoType, Integer> counts = count(new WeightedRandomGenerator(weights, new Random(1L)), 1_000);

        assertEquals(Map.of(TetrominoType.T, 1_000), counts);
    }

    @Test
    void peekShowsExactlyWhatNextReturnsWithoutAdvancing() {
        PieceGenerator generator = new WeightedRandomGenerator(Map.of(
                TetrominoType.I, 1, TetrominoType.O, 1, TetrominoType.T, 1), new Random(3L));

        for (int i = 0; i < 50; i++) {
            TetrominoType peeked = generator.peek();
            assertEquals(peeked, generator.peek());
            assertEquals(peeked, generator.next());
        }
    }

    @Test
    void theSameSeedRepeatsTheSameSequence() {
        Map<TetrominoType, Integer> weights = Map.of(TetrominoType.I, 5, TetrominoType.Z, 2, TetrominoType.L, 3);
        PieceGenerator a = new WeightedRandomGenerator(weights, new Random(11L));
        PieceGenerator b = new WeightedRandomGenerator(weights, new Random(11L));

        boolean allSame = true;
        for (int i = 0; i < 100; i++) {
            allSame &= a.next() == b.next();
        }
        assertTrue(allSame);
    }

    @Test
    void eachDrawIsIndependentSoRunsOfTheSamePieceCanHappen() {
        // 7-bag과 달리 같은 블록이 연속으로 나올 수 있다 (매번 독립적으로 뽑기 때문).
        PieceGenerator generator = new WeightedRandomGenerator(Map.of(
                TetrominoType.I, 1, TetrominoType.O, 1), new Random(5L));
        boolean repeated = false;
        TetrominoType previous = generator.next();
        for (int i = 0; i < 100 && !repeated; i++) {
            TetrominoType current = generator.next();
            repeated = current == previous;
            previous = current;
        }
        assertTrue(repeated);
    }

    @Test
    void invalidWeightsAreRejected() {
        Random random = new Random(1L);
        assertThrows(IllegalArgumentException.class,
                () -> new WeightedRandomGenerator(Map.of(TetrominoType.I, -1, TetrominoType.O, 2), random));
        assertThrows(IllegalArgumentException.class,
                () -> new WeightedRandomGenerator(Map.of(TetrominoType.I, 0), random));
        assertThrows(IllegalArgumentException.class, () -> new WeightedRandomGenerator(Map.of(), random));
        Map<TetrominoType, Integer> withNull = new HashMap<>();
        withNull.put(TetrominoType.I, null);
        assertThrows(NullPointerException.class, () -> new WeightedRandomGenerator(withNull, random));
    }
}
