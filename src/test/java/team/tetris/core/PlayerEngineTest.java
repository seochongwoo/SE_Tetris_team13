package team.tetris.core;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
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
        assertEquals(new ActivePiece(TetrominoType.I, 0, new Position(0, 0)), snapshot.activePiece());
    }

    @Test
    void moveActionsAreBlockedAtWallsAndProduceNoDropOrLockOrClearEvents() {
        PlayerEngine engine = new PlayerEngine(4, 6, new ConstantGenerator(TetrominoType.I));
        // I(rotation0)는 폭4 보드에서 스폰 즉시 양쪽 벽에 닿아있음

        EngineStep left = engine.apply(GameAction.MOVE_LEFT);
        EngineStep right = engine.apply(GameAction.MOVE_RIGHT);

        assertEquals(new Position(0, 0), left.snapshot().activePiece().origin());
        assertEquals(new Position(0, 0), right.snapshot().activePiece().origin());
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

        // I(회전0)는 origin (0,0)에서 시작해 한 줄 아래 칸들이 바닥(5행)까지 4칸 내려간다.
        assertEquals(4, step.dropResult().cellsDropped());
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
        assertEquals(new Position(0, 0), step.snapshot().activePiece().origin());
    }

    /** 호출 횟수를 세는 고정 블록 생성기. 게임오버 뒤 불필요한 스폰이 없는지 확인할 때 쓴다. */
    private static final class CountingGenerator implements PieceGenerator {
        private final TetrominoType type;
        private int nextCalls;

        CountingGenerator(TetrominoType type) {
            this.type = type;
        }

        @Override
        public TetrominoType next() {
            nextCalls++;
            return type;
        }

        @Override
        public TetrominoType peek() {
            return type;
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"HARD_DROP", "SOFT_DROP", "TICK"})
    void stackingUpToTheSpawnAreaEndsTheGameAndFreezesFurtherActions(String input) {
        CountingGenerator generator = new CountingGenerator(TetrominoType.O);
        PlayerEngine engine = new PlayerEngine(10, 20, generator);
        // 가운데 두 열에 O 9개를 쌓으면 2행까지 차오르고, 열 번째 O는 0~1행에서 스폰되어
        // 바로 그 위에 얹힌 채 더는 내려갈 수 없다.
        for (int i = 0; i < 9; i++) {
            engine.apply(GameAction.HARD_DROP);
        }
        EngineSnapshot before = engine.snapshot();
        assertEquals(new ActivePiece(TetrominoType.O, 0, new Position(3, 0)), before.activePiece());
        assertEquals(EnginePhase.RUNNING, before.phase());
        assertEquals(10, generator.nextCalls);

        EngineStep step = input.equals("TICK") ? engine.tick() : engine.apply(GameAction.valueOf(input));

        // 마지막 블록은 정상적으로 고정되고, 다음 블록을 놓을 곳이 없어 게임이 끝난다.
        assertEquals(EnginePhase.GAME_OVER, step.snapshot().phase());
        assertNull(step.snapshot().activePiece());
        assertEquals(new Position(3, 0), step.lockResult().origin());
        assertTrue(step.clearResult().isEmpty());
        if (input.equals("HARD_DROP")) {
            assertEquals(0, step.dropResult().cellsDropped());
        } else {
            assertNull(step.dropResult());
        }
        assertEquals(11, generator.nextCalls);
        Cell[][] finalBoard = step.snapshot().board();
        for (int row = 0; row < 20; row++) {
            assertEquals(Cell.occupiedBy(TetrominoType.O), finalBoard[row][4]);
            assertEquals(Cell.occupiedBy(TetrominoType.O), finalBoard[row][5]);
        }

        // 종료 후 재개를 포함한 모든 입력과 자동 하강은 상태를 바꾸지 않는다.
        for (GameAction action : GameAction.values()) {
            EngineStep ignored = engine.apply(action);
            assertEquals(EnginePhase.GAME_OVER, ignored.snapshot().phase());
            assertNull(ignored.snapshot().activePiece());
            assertNull(ignored.dropResult());
            assertNull(ignored.lockResult());
            assertNull(ignored.clearResult());
        }
        EngineStep tick = engine.tick();
        assertEquals(EnginePhase.GAME_OVER, tick.snapshot().phase());
        assertNull(tick.snapshot().activePiece());
        assertNull(tick.dropResult());
        assertNull(tick.lockResult());
        assertNull(tick.clearResult());
        assertBoardEquals(finalBoard, tick.snapshot().board());
        assertEquals(11, generator.nextCalls);
    }

    @ParameterizedTest
    @EnumSource(TetrominoType.class)
    void spawnAndEveryRotationAfterSpawnStayFullyInsideTheBoard(TetrominoType type) {
        PlayerEngine engine = new PlayerEngine(10, 20, new ConstantGenerator(type));
        assertFullyVisible(engine.snapshot().activePiece());

        for (int turn = 1; turn <= TetrominoType.ROTATION_STATES; turn++) {
            ActivePiece piece = engine.apply(GameAction.ROTATE_CW).snapshot().activePiece();
            assertEquals(turn % TetrominoType.ROTATION_STATES, piece.rotation());
            assertFullyVisible(piece);
        }
    }

    @Test
    void hardDropOfARotatedPieceLocksItOnTheBottom() {
        PlayerEngine engine = new PlayerEngine(10, 20, new ConstantGenerator(TetrominoType.I));
        EngineStep rotation = engine.apply(GameAction.ROTATE_CW);
        assertEquals(new ActivePiece(TetrominoType.I, 1, new Position(3, 0)),
                rotation.snapshot().activePiece());
        assertEquals(EnginePhase.RUNNING, rotation.snapshot().phase());

        EngineStep drop = engine.apply(GameAction.HARD_DROP);

        assertEquals(16, drop.dropResult().cellsDropped());
        assertEquals(new Position(3, 16), drop.lockResult().origin());
        assertEquals(EnginePhase.RUNNING, drop.snapshot().phase());
        for (int row = 16; row < 20; row++) {
            assertEquals(Cell.occupiedBy(TetrominoType.I), drop.snapshot().board()[row][5]);
        }
    }

    private static void assertFullyVisible(ActivePiece piece) {
        for (Position offset : piece.type().cellsAt(piece.rotation())) {
            assertTrue(piece.origin().y() + offset.y() >= 0, piece + " has a cell above the board");
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
