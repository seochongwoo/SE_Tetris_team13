package team.tetris.core;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
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

    @ParameterizedTest
    @ValueSource(strings = {"HARD_DROP", "SOFT_DROP", "TICK"})
    void lockingAboveTheTopEndsTheGameWithoutDiscardingCellsOrSpawning(String input) {
        class StackGenerator implements PieceGenerator {
            private int nextCalls;

            @Override
            public TetrominoType next() {
                TetrominoType type = peek();
                nextCalls++;
                return type;
            }

            @Override
            public TetrominoType peek() {
                return nextCalls == 9 ? TetrominoType.J : TetrominoType.O;
            }
        }
        StackGenerator generator = new StackGenerator();
        PlayerEngine engine = new PlayerEngine(10, 20, generator);
        // 왼쪽 두 열에 O 9개를 쌓고, 상단에서 J를 회전해 벽 위로 옮긴다.
        for (int i = 0; i < 9; i++) {
            for (int move = 0; move < 5; move++) {
                engine.apply(GameAction.MOVE_LEFT);
            }
            engine.apply(GameAction.HARD_DROP);
        }
        engine.apply(GameAction.ROTATE_CW);
        for (int move = 0; move < 3; move++) {
            engine.apply(GameAction.MOVE_LEFT);
        }
        EngineSnapshot before = engine.snapshot();
        assertEquals(new ActivePiece(TetrominoType.J, 1, new Position(0, -1)), before.activePiece());
        assertEquals(EnginePhase.RUNNING, before.phase());

        EngineStep step = input.equals("TICK") ? engine.tick() : engine.apply(GameAction.valueOf(input));

        assertEquals(EnginePhase.GAME_OVER, step.snapshot().phase());
        assertEquals(before.activePiece(), step.snapshot().activePiece());
        assertNull(step.lockResult());
        assertNull(step.clearResult());
        if (input.equals("HARD_DROP")) {
            assertEquals(0, step.dropResult().cellsDropped());
        } else {
            assertNull(step.dropResult());
        }
        assertEquals(10, generator.nextCalls);
        assertBoardEquals(before.board(), step.snapshot().board());

        // 종료 후 재개를 포함한 모든 입력과 자동 하강은 상태를 바꾸지 않는다.
        for (GameAction action : GameAction.values()) {
            EngineStep ignored = engine.apply(action);
            assertEquals(EnginePhase.GAME_OVER, ignored.snapshot().phase());
            assertEquals(before.activePiece(), ignored.snapshot().activePiece());
            assertNull(ignored.dropResult());
            assertNull(ignored.lockResult());
            assertNull(ignored.clearResult());
        }
        EngineStep tick = engine.tick();
        assertEquals(EnginePhase.GAME_OVER, tick.snapshot().phase());
        assertEquals(before.activePiece(), tick.snapshot().activePiece());
        assertNull(tick.dropResult());
        assertNull(tick.lockResult());
        assertNull(tick.clearResult());
        assertBoardEquals(before.board(), tick.snapshot().board());
        assertEquals(10, generator.nextCalls);
    }

    @Test
    void hardDropCanBringARotatedPieceAboveTheTopBackInsideBeforeLocking() {
        PlayerEngine engine = new PlayerEngine(10, 20, new ConstantGenerator(TetrominoType.I));
        EngineStep rotation = engine.apply(GameAction.ROTATE_CW);
        assertEquals(new ActivePiece(TetrominoType.I, 1, new Position(3, -1)),
                rotation.snapshot().activePiece());
        assertEquals(EnginePhase.RUNNING, rotation.snapshot().phase());

        EngineStep drop = engine.apply(GameAction.HARD_DROP);

        assertEquals(17, drop.dropResult().cellsDropped());
        assertEquals(new Position(3, 16), drop.lockResult().origin());
        assertEquals(EnginePhase.RUNNING, drop.snapshot().phase());
        for (int row = 16; row < 20; row++) {
            assertEquals(Cell.occupiedBy(TetrominoType.I), drop.snapshot().board()[row][5]);
        }
    }

    private static void assertBoardEquals(Cell[][] expected, Cell[][] actual) {
        assertEquals(expected.length, actual.length);
        for (int row = 0; row < expected.length; row++) {
            assertArrayEquals(expected[row], actual[row]);
        }
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
