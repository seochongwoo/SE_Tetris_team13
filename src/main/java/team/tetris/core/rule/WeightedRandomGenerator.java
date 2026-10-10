package team.tetris.core.rule;

import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.random.RandomGenerator;
import team.tetris.core.TetrominoType;

/**
 * 블록마다 가중치를 두고 룰렛 휠 선택(Roulette Wheel Selection, fitness proportionate selection)으로
 * 다음 블록을 뽑는 생성기. 블록이 뽑힐 확률은 (자기 가중치 / 가중치 합)이고, 매번 독립적으로 뽑는다.
 *
 * <p>예: 모든 블록의 가중치가 10이면 각각 1/7, I만 12면 I는 12/72 ≈ 16.7%. 가중치가 0인 블록은
 * 나오지 않는다. core는 난이도를 모르고, 난이도별 가중치는 application이 정해 넘긴다.
 *
 * <p>구현: 가중치를 누적 합으로 늘어놓고 [0, 합) 범위의 난수가 떨어지는 구간의 블록을 고른다.
 * 블록이 7종뿐이라 선형 탐색으로 충분하다.
 */
public final class WeightedRandomGenerator implements PieceGenerator {

    private final TetrominoType[] types;
    private final int[] cumulative;
    private final int total;
    private final RandomGenerator random;
    private TetrominoType upcoming;

    public WeightedRandomGenerator(Map<TetrominoType, Integer> weights, RandomGenerator random) {
        Objects.requireNonNull(weights, "weights");
        this.random = Objects.requireNonNull(random, "random");
        Map<TetrominoType, Integer> ordered = new EnumMap<>(TetrominoType.class);
        ordered.putAll(weights);
        types = new TetrominoType[ordered.size()];
        cumulative = new int[ordered.size()];
        int sum = 0;
        int index = 0;
        for (Map.Entry<TetrominoType, Integer> entry : ordered.entrySet()) {
            int weight = Objects.requireNonNull(entry.getValue(), "weight");
            if (weight < 0) {
                throw new IllegalArgumentException("Weight of " + entry.getKey() + " must be non-negative");
            }
            sum = Math.addExact(sum, weight);
            types[index] = entry.getKey();
            cumulative[index] = sum;
            index++;
        }
        if (sum == 0) {
            throw new IllegalArgumentException("At least one piece needs a positive weight");
        }
        total = sum;
        upcoming = spin();
    }

    @Override
    public TetrominoType next() {
        TetrominoType current = upcoming;
        upcoming = spin();
        return current;
    }

    @Override
    public TetrominoType peek() {
        return upcoming;
    }

    private TetrominoType spin() {
        int point = random.nextInt(total);
        for (int i = 0; i < types.length; i++) {
            if (point < cumulative[i]) {
                return types[i];
            }
        }
        throw new AssertionError("point " + point + " is outside the wheel");
    }
}
