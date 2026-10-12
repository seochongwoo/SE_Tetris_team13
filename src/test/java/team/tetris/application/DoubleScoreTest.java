package team.tetris.application;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayDeque;
import java.util.List;
import java.util.Queue;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import team.tetris.core.Cell;
import team.tetris.core.EnginePhase;
import team.tetris.core.EngineSnapshot;
import team.tetris.core.GameAction;
import team.tetris.core.Piece;
import team.tetris.core.Position;
import team.tetris.core.TetrominoType;
import team.tetris.core.item.BombItem;
import team.tetris.core.item.DoubleScoreItem;
import team.tetris.core.item.Item;
import team.tetris.core.port.TetrisEnginePort;
import team.tetris.core.result.ClearResult;
import team.tetris.core.result.ClearedRow;
import team.tetris.core.result.EngineStep;
import team.tetris.core.result.ItemActivation;
import team.tetris.core.result.LockResult;

/** 점수 2배 아이템($): 발동한 고정부터 줄 삭제 3번 동안 그 동작의 점수를 2배로 한다. */
class DoubleScoreTest {

    private final StepEngine engine = new StepEngine();
    private final SinglePlayerSession session =
            new SinglePlayerSession(engine, new ScorePolicy(), new SpeedPolicy());

    /** 하드 드롭 없이(하강 0칸) lines줄을 지우는 고정 한 번. */
    private long lock(int lines, Item... activated) {
        long before = session.snapshot().score();
        engine.queue.add(engine.lockStep(lines, activated));
        session.handle(GameCommand.HARD_DROP);
        return session.snapshot().score() - before;
    }

    @Test
    void doublesTheNextThreeLineClearsStartingWithTheItemsOwnLock() {
        assertEquals(200, lock(1, new DoubleScoreItem()));
        assertEquals(2, session.snapshot().doubleScoreClears());
        assertEquals(0, lock(0)); // 줄을 지우지 않은 고정은 횟수를 쓰지 않는다
        assertEquals(2, session.snapshot().doubleScoreClears());
        assertEquals(800, lock(2));
        assertEquals(200, lock(1));
        assertEquals(0, session.snapshot().doubleScoreClears());
        assertEquals(100, lock(1));
    }

    @Test
    void anItemThatClearsNothingStillStartsTheEffect() {
        assertEquals(0, lock(0, new DoubleScoreItem()));
        assertEquals(3, session.snapshot().doubleScoreClears());
        assertEquals(200, lock(1));
    }

    @Test
    void gettingAnotherOneResetsTheCountInsteadOfAddingUp() {
        lock(1, new DoubleScoreItem());
        lock(1);
        assertEquals(1, session.snapshot().doubleScoreClears());

        lock(0, new DoubleScoreItem());

        assertEquals(3, session.snapshot().doubleScoreClears());
    }

    @Test
    void otherItemsDoNotChangeTheScore() {
        assertEquals(100, lock(1, new BombItem()));
        assertEquals(0, session.snapshot().doubleScoreClears());
    }

    @Test
    void theEffectIsCarriedToTheFinalResult() {
        lock(1, new DoubleScoreItem());
        engine.queue.add(engine.lockStep(1, EnginePhase.GAME_OVER));
        session.handle(GameCommand.HARD_DROP);

        assertEquals(400, session.result().orElseThrow().score());
    }

    /** 미리 넣어 둔 결과를 차례로 돌려주는 엔진. */
    private static final class StepEngine implements TetrisEnginePort {
        final Queue<EngineStep> queue = new ArrayDeque<>();
        private final Cell[][] board = {{Cell.EMPTY}};

        EngineStep lockStep(int lines, Item... activated) {
            return lockStep(lines, EnginePhase.RUNNING, activated);
        }

        EngineStep lockStep(int lines, EnginePhase after, Item... activated) {
            List<ClearedRow> rows = IntStream.range(0, lines).mapToObj(row -> new ClearedRow(row, List.of())).toList();
            List<ItemActivation> activations = List.of(activated).stream()
                    .map(item -> new ItemActivation(item, new Position(0, 0))).toList();
            return new EngineStep(new EngineSnapshot(board, null, Piece.of(TetrominoType.I), after),
                    null, new LockResult(TetrominoType.I, new Position(0, 0), 0), new ClearResult(rows), activations);
        }

        @Override
        public EngineSnapshot snapshot() {
            return new EngineSnapshot(board, null, Piece.of(TetrominoType.I), EnginePhase.RUNNING);
        }

        @Override
        public EngineStep apply(GameAction action) {
            return queue.remove();
        }

        @Override
        public EngineStep tick() {
            return queue.remove();
        }
    }
}
