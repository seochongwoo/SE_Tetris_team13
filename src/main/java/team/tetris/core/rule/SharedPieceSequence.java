package team.tetris.core.rule;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import team.tetris.core.TetrominoType;

/**
 * 생성기 하나가 뽑은 블록 순서를 여러 플레이어가 똑같이 받게 한다 (3차 안건 ⑰: 두 플레이어의 기본 블록은
 * 같은 순서). 플레이어마다 {@link #newReader()}로 받은 생성기를 쓰면, 각자 몇 번째 블록을 꺼내든 같은
 * 위치에서는 같은 모양이 나온다. 한 플레이어가 앞서 나가도 다른 플레이어의 순서는 바뀌지 않는다.
 *
 * <p>아이템 블록은 이 순서 위에서 플레이어마다 따로 정한다({@link ItemPieceSource}로 각자 감싼다).
 * 하나의 게임 루프에서만 호출한다.
 */
public final class SharedPieceSequence {

    private final PieceGenerator source;
    /** 아직 모든 플레이어가 꺼내 가지는 않은 블록. 맨 앞은 순서상 {@code offset}번째다. */
    private final List<TetrominoType> drawn = new ArrayList<>();
    private final List<Reader> readers = new ArrayList<>();
    private long offset;

    public SharedPieceSequence(PieceGenerator source) {
        this.source = Objects.requireNonNull(source, "source");
    }

    /** 순서의 처음부터 읽는 플레이어용 생성기. */
    public PieceGenerator newReader() {
        if (offset > 0) {
            throw new IllegalStateException("Readers must be created before any piece is taken");
        }
        Reader reader = new Reader();
        readers.add(reader);
        return reader;
    }

    private TetrominoType at(long position) {
        while (offset + drawn.size() <= position) {
            drawn.add(Objects.requireNonNull(source.next(), "piece"));
        }
        return drawn.get((int) (position - offset));
    }

    /** 모든 플레이어가 지나간 블록은 버려 오래 해도 기억이 늘지 않게 한다. */
    private void forgetPassed() {
        long slowest = Long.MAX_VALUE;
        for (Reader reader : readers) {
            slowest = Math.min(slowest, reader.position);
        }
        int passed = (int) Math.min(slowest - offset, drawn.size());
        if (passed > 0) {
            drawn.subList(0, passed).clear();
            offset += passed;
        }
    }

    private final class Reader implements PieceGenerator {
        private long position;

        @Override
        public TetrominoType next() {
            TetrominoType type = at(position);
            position++;
            forgetPassed();
            return type;
        }

        @Override
        public TetrominoType peek() {
            return at(position);
        }
    }
}
