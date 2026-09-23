package team.tetris.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;
import team.tetris.core.result.ClearedRow;
import team.tetris.core.result.EngineStep;
import team.tetris.core.rule.PieceGenerator;

/**
 * PlayerEngine 통합 테스트. 랜덤 생성기 대신 항상 같은 블록만 내놓는 테스트 더블을 써서
 * 시나리오를 결정적으로(deterministic) 재현한다.
 */
class PlayerEngineTest {

    /** 항상 같은 블록만 내놓는, 테스트 전용 고정 시퀀스 생성기. */
    private static final class ConstantGenerator implements PieceGenerator {
        private final TetrominoType type;

        ConstantGenerator(TetrominoType type) {
            this.type = type;
        }

        @Override
        public TetrominoType next() {
            return type;
        }

        @Override
        public TetrominoType peek() {
            return type;
        }
    }

    @Test
    void constructingTheEngineSpawnsTheFirstPieceAlreadyRunning() {
        PlayerEngine engine = new PlayerEngine(4, 6, new ConstantGenerator(TetrominoType.I));

        EngineSnapshot snapshot = engine.snapshot();

        assertEquals(EnginePhase.RUNNING, snapshot.phase());
        assertEquals(new ActivePiece(TetrominoType.I, 0, new Position(0, -1)), snapshot.activePiece());
    }

    @Test
    void moveActionsAreBlockedAtWallsAndProduceNoDropOrLockOrClearEvents() {
        PlayerEngine engine = new PlayerEngine(4, 6, new ConstantGenerator(TetrominoType.I));
        // I(rotation0)는 폭4 보드에서 스폰 즉시 양쪽 벽에 닿아있음

        EngineStep left = engine.apply(GameAction.MOVE_LEFT);
        EngineStep right = engine.apply(GameAction.MOVE_RIGHT);

        assertEquals(new Position(0, -1), left.snapshot().activePiece().origin());
        assertEquals(new Position(0, -1), right.snapshot().activePiece().origin());
        assertNull(left.dropResult());
        assertNull(left.lockResult());
        assertNull(left.clearResult());
    }

    @Test
    void rotateAppliesRotationWithoutDropLockOrClearEvents() {
        PlayerEngine engine = new PlayerEngine(6, 6, new ConstantGenerator(TetrominoType.T));

        EngineStep step = engine.apply(GameAction.ROTATE_CW);

        assertEquals(1, step.snapshot().activePiece().rotation());
        assertNull(step.dropResult());
        assertNull(step.lockResult());
        assertNull(step.clearResult());
    }

    @Test
    void pauseIgnoresTicksAndMovesUntilResumed() {
        PlayerEngine engine = new PlayerEngine(6, 6, new ConstantGenerator(TetrominoType.T));

        engine.apply(GameAction.PAUSE);
        assertEquals(EnginePhase.PAUSED, engine.snapshot().phase());

        Position beforeTick = engine.snapshot().activePiece().origin();
        engine.tick();
        engine.apply(GameAction.MOVE_LEFT);
        assertEquals(beforeTick, engine.snapshot().activePiece().origin());

        engine.apply(GameAction.RESUME);
        assertEquals(EnginePhase.RUNNING, engine.snapshot().phase());
    }

    @Test
    void tickDescendsOneCellThenLocksWhenNoRoomIsLeft() {
        // 폭4 보드에 O가 중앙(원점 (0,0), 셀은 row0,1 col1,2)으로 스폰되고 높이가 3이라
        // 한 번만 내려갈 수 있고 그 다음엔 바닥에 닿아 고정된다.
        PlayerEngine engine = new PlayerEngine(4, 3, new ConstantGenerator(TetrominoType.O));

        EngineStep firstTick = engine.tick();
        assertEquals(1, firstTick.dropResult().cellsDropped());
        assertNull(firstTick.lockResult());

        EngineStep secondTick = engine.tick();
        assertNull(secondTick.dropResult());
        assertEquals(TetrominoType.O, secondTick.lockResult().type());
    }

    @Test
    void hardDropFillsBottomRowLocksAndClearsItInOneStep() {
        PlayerEngine engine = new PlayerEngine(4, 6, new ConstantGenerator(TetrominoType.I));

        EngineStep step = engine.apply(GameAction.HARD_DROP);

        assertEquals(5, step.dropResult().cellsDropped());
        assertEquals(new Position(0, 4), step.lockResult().origin());
        assertEquals(1, step.clearResult().lineCount());
        assertEquals(List.of(new ClearedRow(5)), step.clearResult().clearedRows());
        assertEquals(EnginePhase.RUNNING, step.snapshot().phase());
        // 지워지고 나면 보드가 다시 완전히 비어있어야 함 (이 보드엔 이 줄 하나만 채워져 있었으므로)
        for (Cell[] row : step.snapshot().board()) {
            for (Cell cell : row) {
                assertTrue(cell.isEmpty());
            }
        }
        // 다음 블록이 같은 스폰 위치에 다시 나타남
        assertEquals(new Position(0, -1), step.snapshot().activePiece().origin());
    }

    @Test
    void reachingTheTopEndsTheGameAndFreezesFurtherActions() {
        // 폭4, 높이4 보드에서 O만 계속 중앙에 하드드롭하면 두 번째 드롭에서 바로 쌓여
        // 세 번째 스폰이 막혀 게임오버가 된다.
        PlayerEngine engine = new PlayerEngine(4, 4, new ConstantGenerator(TetrominoType.O));

        EngineStep firstDrop = engine.apply(GameAction.HARD_DROP);
        assertEquals(2, firstDrop.dropResult().cellsDropped());
        assertEquals(EnginePhase.RUNNING, firstDrop.snapshot().phase());

        EngineStep secondDrop = engine.apply(GameAction.HARD_DROP);
        assertEquals(0, secondDrop.dropResult().cellsDropped());
        assertEquals(EnginePhase.GAME_OVER, secondDrop.snapshot().phase());
        assertNull(secondDrop.snapshot().activePiece());

        EngineStep afterGameOver = engine.apply(GameAction.HARD_DROP);
        assertEquals(EnginePhase.GAME_OVER, afterGameOver.snapshot().phase());
        assertNull(afterGameOver.dropResult());
        assertNull(afterGameOver.lockResult());

        EngineStep tickAfterGameOver = engine.tick();
        assertNull(tickAfterGameOver.dropResult());
    }
}
