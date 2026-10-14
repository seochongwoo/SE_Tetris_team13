package team.tetris.application.debug;

import java.util.Random;
import team.tetris.application.PieceWeightPolicy;
import team.tetris.application.model.Difficulty;
import team.tetris.core.TetrominoType;
import team.tetris.core.rule.PieceGenerator;
import team.tetris.core.rule.WeightedRandomGenerator;

/** 난수 흐름은 유지하면서 이후 추첨의 난이도를 바꾼다. 화면의 미리보기 보존은 세션이 담당한다. */
public final class DebugPieceGenerator implements PieceGenerator {
    private final Random random;
    private WeightedRandomGenerator delegate;

    public DebugPieceGenerator(Difficulty difficulty, long seed) {
        random = new Random(seed);
        setDifficulty(difficulty);
    }

    public void setDifficulty(Difficulty difficulty) {
        delegate = new WeightedRandomGenerator(PieceWeightPolicy.weightsFor(difficulty), random);
    }

    @Override public TetrominoType next() { return delegate.next(); }
    @Override public TetrominoType peek() { return delegate.peek(); }
}
