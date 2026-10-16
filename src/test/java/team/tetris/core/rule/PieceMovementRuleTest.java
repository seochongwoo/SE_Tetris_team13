package team.tetris.core.rule;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import team.tetris.core.*;
import team.tetris.core.item.SoloBlock;
import team.tetris.core.item.WeightBlock;
import team.tetris.core.result.EngineStep;

class PieceMovementRuleTest {
    @Test
    void customRuleCanDisableHorizontalMovementAndRotationForARotatableShape() {
        PieceMovementRule rule = new PieceMovementRule() {
            public boolean allowsHorizontalMovement(boolean touchedDown) { return false; }
            public boolean allowsRotation(boolean touchedDown) { return false; }
        };
        Shape shape = withRule(TetrominoType.T, rule);
        ActivePiece initial = new ActivePiece(shape, 0, new Position(2, 0));
        PlayerEngine engine = engine(new Board(8, 8).snapshot(), initial);

        for (GameAction action : new GameAction[] {GameAction.MOVE_LEFT, GameAction.MOVE_RIGHT,
                GameAction.ROTATE_CW, GameAction.ROTATE_CCW}) {
            engine.apply(action);
            assertEquals(initial, engine.snapshot().activePiece());
        }
        engine.tick();
        assertEquals(initial.movedTo(new Position(2, 1)), engine.snapshot().activePiece());
    }

    @ParameterizedTest
    @ValueSource(strings = {"gravity", "soft", "hard"})
    void customDescentIsSharedByEveryDropAndBothPreviews(String mode) {
        // 무게추가 아닌 새 블록: 경로의 칸을 지우지만 3행에서 멈춘다.
        PieceMovementRule rule = new PieceMovementRule() {
            public ActivePiece descend(Board board, ActivePiece piece) {
                if (piece.origin().y() >= 3) return null;
                ActivePiece next = piece.movedTo(piece.origin().translate(0, 1));
                board.removeCell(next.origin());
                return next;
            }
        };
        Shape shape = withRule(SoloBlock.SHAPE, rule);
        Cell[][] cells = new Board(8, 8).snapshot();
        cells[2][2] = Cell.occupiedBy(TetrominoType.J);
        cells[6][2] = Cell.occupiedBy(TetrominoType.Z);
        ActivePiece initial = new ActivePiece(shape, 0, new Position(2, 0));
        PlayerEngine engine = engine(cells, initial);

        var preview = HardDropPreview.project(cells, initial);
        assertEquals(new Position(2, 3), preview.landing().origin());
        assertEquals(Set.of(new Position(2, 2)), preview.removedCells());
        assertEquals(preview.landing(), DropRule.landingPiece(cells, initial));
        assertArrayEquals(cells, engine.snapshot().board());
        assertEquals(TetrominoType.J, cells[2][2].occupiedBy());

        EngineStep step = null;
        int distance = 0;
        for (int i = 0; i < 4; i++) {
            step = switch (mode) {
                case "gravity" -> engine.tick();
                case "soft" -> engine.apply(GameAction.SOFT_DROP);
                default -> engine.apply(GameAction.HARD_DROP);
            };
            if (step.dropResult() != null) distance += step.dropResult().cellsDropped();
            if (step.lockResult() != null) break;
        }
        assertNotNull(step.lockResult());
        assertEquals(3, distance);
        assertEquals(preview.landing().origin(), step.lockResult().origin());
        assertTrue(step.snapshot().board()[2][2].isEmpty());
        assertSame(shape, step.snapshot().board()[3][2].occupiedBy());
        assertEquals(TetrominoType.Z, step.snapshot().board()[6][2].occupiedBy());
    }

    @Test
    void anotherShapeCanReuseWeightMovementWithoutSharingContactState() {
        Shape shape = withRule(SoloBlock.SHAPE, WeightBlock.SHAPE.movementRule());
        Cell[][] cells = new Board(8, 8).snapshot();
        cells[2][2] = Cell.occupiedBy(TetrominoType.J);
        ActivePiece initial = new ActivePiece(shape, 0, new Position(2, 0));
        PlayerEngine engine = engine(cells, initial);
        PlayerEngine other = engine(cells, initial);

        engine.apply(GameAction.MOVE_LEFT);
        assertEquals(new Position(1, 0), engine.snapshot().activePiece().origin());
        engine.apply(GameAction.MOVE_RIGHT);
        engine.tick(); // (2,2)에 처음 접촉
        engine.apply(GameAction.MOVE_LEFT);
        assertEquals(new Position(2, 1), engine.snapshot().activePiece().origin());
        engine.tick(); // 접촉한 칸을 지운 뒤에도 잠금 유지
        assertTrue(engine.snapshot().board()[2][2].isEmpty());
        engine.apply(GameAction.MOVE_RIGHT);
        assertEquals(new Position(2, 2), engine.snapshot().activePiece().origin());

        other.apply(GameAction.MOVE_LEFT);
        assertEquals(new Position(1, 0), other.snapshot().activePiece().origin());
        engine.apply(GameAction.HARD_DROP);
        Position spawned = engine.snapshot().activePiece().origin();
        engine.apply(GameAction.MOVE_LEFT);
        assertEquals(spawned.translate(-1, 0), engine.snapshot().activePiece().origin());
    }

    @ParameterizedTest
    @ValueSource(ints = {2, 6})
    void initiallyTouchingWeightCannotMoveBeforeItsFirstTick(int row) {
        Cell[][] cells = new Board(8, 8).snapshot();
        if (row == 2) cells[4][3] = Cell.occupiedBy(TetrominoType.J);
        ActivePiece initial = new ActivePiece(new WeightBlock().toItemPiece(null, null),
                0, new Position(2, row));
        PlayerEngine engine = engine(cells, initial);

        engine.apply(GameAction.MOVE_LEFT);
        assertEquals(initial, engine.snapshot().activePiece());
        engine.apply(GameAction.MOVE_RIGHT);
        assertEquals(initial, engine.snapshot().activePiece());
    }

    private static Shape withRule(Shape shape, PieceMovementRule rule) {
        return new Shape() {
            public Position[] cellsAt(int rotation) { return shape.cellsAt(rotation); }
            public int rotationStates() { return shape.rotationStates(); }
            public PieceMovementRule movementRule() { return rule; }
        };
    }

    private static PlayerEngine engine(Cell[][] cells, ActivePiece initial) {
        return new PlayerEngine(cells, initial, new PieceSource() {
            public Piece next() { return peek(); }
            public Piece peek() { return Piece.of(TetrominoType.O); }
        });
    }
}
