package team.tetris.core.rule;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.Random;
import team.tetris.core.TetrominoType;

/**
 * "7-bag" 랜덤 생성기. 7종류 블록을 한 번씩만 담은 가방을 무작위로 섞은 뒤 하나씩 꺼내고,
 * 가방이 비면 다시 7종류를 채워 섞는다 - 요구사항(9p)의 "다음에 나타날 블럭은 동일한 확률로
 * 무작위로 결정됨"을 만족하면서, 한 종류가 너무 오래 안 나오는 것도 막아준다.
 *
 * <p>seed를 생성자로 주입받기 때문에 테스트에서 동일 시드로 항상 같은 시퀀스를 재현할 수 있다.
 */
public final class SevenBagGenerator implements PieceGenerator {

    private final Random random;
    private final Deque<TetrominoType> bag = new ArrayDeque<>();

    public SevenBagGenerator(long seed) {
        this.random = new Random(seed);
        refillBag();
    }

    @Override
    public TetrominoType next() {
        TetrominoType next = peek();
        bag.poll();
        return next;
    }

    @Override
    public TetrominoType peek() {
        if (bag.isEmpty()) {
            refillBag();
        }
        return bag.peek();
    }

    private void refillBag() {
        List<TetrominoType> shuffled = new ArrayList<>(List.of(TetrominoType.values()));
        Collections.shuffle(shuffled, random);
        bag.addAll(shuffled);
    }
}
