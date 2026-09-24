package team.tetris.core;

import team.tetris.core.port.TetrisEnginePort;
import team.tetris.core.result.ClearResult;
import team.tetris.core.result.DropResult;
import team.tetris.core.result.EngineStep;
import team.tetris.core.result.LockResult;
import team.tetris.core.rule.PieceGenerator;
import team.tetris.core.rule.RotationResult;
import team.tetris.core.rule.RotationSystem;

/**
 * core의 얼굴 역할을 하는 통합 엔진이자 {@link TetrisEnginePort}의 유일한 구현체.
 * Board, PieceGenerator, RotationSystem을 조립해서 "블록 하나짜리 게임 진행"을 담당한다.
 * application(GameSession)은 이 클래스를 직접 참조하지 않고 {@link TetrisEnginePort} 타입으로만
 * 갖고 있어야 하며, 조립({@code new PlayerEngine(...)})은 bootstrap/AppComposition.java에서만
 * 한다.
 *
 * <p>점수 계산은 절대 하지 않는다 - {@link EngineStep}에 담기는 dropResult/lockResult/
 * clearResult는 전부 "무슨 일이 일어났는지"에 대한 사실이고, 그걸 점수로 환산하는 규칙은
 * application(ScorePolicy)의 책임이다.
 */
public final class PlayerEngine implements TetrisEnginePort {

    private final Board board;
    private final PieceGenerator generator;
    private final RotationSystem rotationSystem = new RotationSystem();

    private ActivePiece activePiece;
    private EnginePhase phase = EnginePhase.READY;

    public PlayerEngine(int width, int height, PieceGenerator generator) {
        this.board = new Board(width, height);
        this.generator = generator;
        spawnNext();
    }

    @Override
    public EngineSnapshot snapshot() {
        return new EngineSnapshot(board.snapshot(), activePiece, generator.peek(), phase);
    }

    /**
     * 키 입력 등 사용자 조작 하나를 적용한다. PAUSE/RESUME을 제외하면, RUNNING 상태가
     * 아닐 때는 아무 효과 없이 현재 상태만 담아 반환한다.
     */
    @Override
    public EngineStep apply(GameAction action) {
        if (action == GameAction.PAUSE) {
            if (phase == EnginePhase.RUNNING) {
                phase = EnginePhase.PAUSED;
            }
            return noEvent();
        }
        if (action == GameAction.RESUME) {
            if (phase == EnginePhase.PAUSED) {
                phase = EnginePhase.RUNNING;
            }
            return noEvent();
        }
        if (phase != EnginePhase.RUNNING) {
            return noEvent();
        }
        return switch (action) {
            case MOVE_LEFT -> tryShift(-1, 0);
            case MOVE_RIGHT -> tryShift(1, 0);
            case SOFT_DROP -> descendOrLock();
            case HARD_DROP -> hardDrop();
            case ROTATE_CW -> rotate(true);
            case ROTATE_CCW -> rotate(false);
            case PAUSE, RESUME -> noEvent(); // 위에서 이미 처리됨, switch 완전성용
        };
    }

    /**
     * 중력에 의한 자동 하강 1회분. application의 게임 루프가 일정 주기로 호출한다.
     * RUNNING 상태가 아니면 아무 효과가 없다.
     */
    @Override
    public EngineStep tick() {
        if (phase != EnginePhase.RUNNING) {
            return noEvent();
        }
        return descendOrLock();
    }

    private EngineStep tryShift(int dx, int dy) {
        Position moved = activePiece.origin().translate(dx, dy);
        if (board.canPlace(activePiece.type(), activePiece.rotation(), moved)) {
            activePiece = new ActivePiece(activePiece.type(), activePiece.rotation(), moved);
        }
        return noEvent();
    }

    private EngineStep rotate(boolean clockwise) {
        RotationResult result = rotationSystem.tryRotate(
                board, activePiece.type(), activePiece.rotation(), activePiece.origin(), clockwise);
        if (result.rotated()) {
            activePiece = new ActivePiece(activePiece.type(), result.rotation(), result.origin());
        }
        return noEvent();
    }

    /**
     * 한 칸 더 내려갈 수 있으면 내려가고 DropResult(1)을 담아 반환한다.
     * 내려갈 수 없으면(바닥/다른 블록에 막힘) 그 자리에 고정 처리하고 dropResult 없이 반환한다.
     * tick()과 SOFT_DROP이 이 동작을 공유한다.
     */
    private EngineStep descendOrLock() {
        Position lowered = activePiece.origin().translate(0, 1);
        if (board.canPlace(activePiece.type(), activePiece.rotation(), lowered)) {
            activePiece = new ActivePiece(activePiece.type(), activePiece.rotation(), lowered);
            return new EngineStep(snapshot(), new DropResult(1), null, null);
        }
        return lockActivePieceAndSpawnNext(null);
    }

    private EngineStep hardDrop() {
        Position position = activePiece.origin();
        int cellsDropped = 0;
        while (board.canPlace(activePiece.type(), activePiece.rotation(), position.translate(0, 1))) {
            position = position.translate(0, 1);
            cellsDropped++;
        }
        activePiece = new ActivePiece(activePiece.type(), activePiece.rotation(), position);
        return lockActivePieceAndSpawnNext(new DropResult(cellsDropped));
    }

    private EngineStep lockActivePieceAndSpawnNext(DropResult dropResult) {
        // 상단 밖에 걸친 블록은 일부만 보드에 저장하지 않고 최종 위치를 유지한 채 종료한다.
        for (Position offset : activePiece.type().cellsAt(activePiece.rotation())) {
            if (activePiece.origin().y() + offset.y() < 0) {
                phase = EnginePhase.GAME_OVER;
                return new EngineStep(snapshot(), dropResult, null, null);
            }
        }
        LockResult lockResult = new LockResult(activePiece.type(), activePiece.origin(), activePiece.rotation());
        board.place(activePiece.type(), activePiece.rotation(), activePiece.origin());
        ClearResult clearResult = board.clearFullLines();
        spawnNext();
        return new EngineStep(snapshot(), dropResult, lockResult, clearResult);
    }

    /**
     * 다음 블록을 뽑아 스폰 위치에 놓을 수 있으면 RUNNING으로, 놓을 수 없으면(더 이상 쌓을
     * 공간이 없으면) GAME_OVER로 전환한다.
     */
    private void spawnNext() {
        TetrominoType type = generator.next();
        Position origin = spawnOrigin(type);
        if (board.canPlace(type, 0, origin)) {
            activePiece = new ActivePiece(type, 0, origin);
            phase = EnginePhase.RUNNING;
        } else {
            activePiece = null;
            phase = EnginePhase.GAME_OVER;
        }
    }

    /**
     * type의 스폰(회전0) 모양을 보드 가로 중앙에 오도록, 맨 위 칸이 보드 0행에 오도록
     * origin을 계산한다. 블록별 상대좌표의 바운딩 박스가 서로 달라도(예: O는 x=1~2, I는
     * x=0~3) 항상 같은 방식으로 중앙 정렬되도록 실제 바운딩 박스를 계산해서 보정한다.
     */
    private Position spawnOrigin(TetrominoType type) {
        Position[] cells = type.cellsAt(0);
        int minX = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int minY = Integer.MAX_VALUE;
        for (Position cell : cells) {
            minX = Math.min(minX, cell.x());
            maxX = Math.max(maxX, cell.x());
            minY = Math.min(minY, cell.y());
        }
        int boxWidth = maxX - minX + 1;
        int originX = (board.width() - boxWidth) / 2 - minX;
        int originY = -minY;
        return new Position(originX, originY);
    }

    private EngineStep noEvent() {
        return new EngineStep(snapshot(), null, null, null);
    }
}
