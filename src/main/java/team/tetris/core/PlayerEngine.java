package team.tetris.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import team.tetris.core.item.Item;
import team.tetris.core.item.ItemContext;
import team.tetris.core.port.TetrisEnginePort;
import team.tetris.core.result.ClearResult;
import team.tetris.core.result.DropResult;
import team.tetris.core.result.EngineStep;
import team.tetris.core.result.ItemActivation;
import team.tetris.core.result.LockResult;
import team.tetris.core.rule.PieceGenerator;
import team.tetris.core.rule.PieceSource;
import team.tetris.core.rule.PlainPieceSource;
import team.tetris.core.rule.RotationResult;
import team.tetris.core.rule.RotationSystem;

/**
 * core의 얼굴 역할을 하는 통합 엔진이자 {@link TetrisEnginePort}의 유일한 구현체.
 * Board, PieceSource, RotationSystem을 조립해서 "블록 하나짜리 게임 진행"을 담당한다.
 * application(GameSession)은 이 클래스를 직접 참조하지 않고 {@link TetrisEnginePort} 타입으로만
 * 갖고 있어야 하며, 조립({@code new PlayerEngine(...)})은 bootstrap/AppComposition.java에서만
 * 한다.
 *
 * <p>점수 계산은 절대 하지 않는다 - {@link EngineStep}에 담기는 dropResult/lockResult/
 * clearResult/itemActivations는 전부 "무슨 일이 일어났는지"에 대한 사실이고, 그걸 점수로 환산하는
 * 규칙은 application(ScorePolicy)의 책임이다.
 *
 * <p>블록이 고정되면 다음 순서로 처리한다: 보드에 기록 → 아이템이 실린 칸마다 아이템 효과 →
 * 꽉 찬 줄과 아이템이 지정한 줄을 한 번에 삭제 → 공급자에 지운 줄 수 알림 → 다음 블록 스폰.
 */
public final class PlayerEngine implements TetrisEnginePort {

    private final Board board;
    private final PieceSource source;
    private final RotationSystem rotationSystem = new RotationSystem();

    private ActivePiece activePiece;
    private EnginePhase phase = EnginePhase.READY;

    public PlayerEngine(int width, int height, PieceSource source) {
        this.board = new Board(width, height);
        this.source = Objects.requireNonNull(source, "source");
        spawnNext();
    }

    /** 아이템 없이 생성기가 정한 모양만 나오는 엔진 (일반 모드). */
    public PlayerEngine(int width, int height, PieceGenerator generator) {
        this(width, height, new PlainPieceSource(generator));
    }

    @Override
    public EngineSnapshot snapshot() {
        return new EngineSnapshot(board.snapshot(), activePiece, source.peek(), phase);
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
        if (board.canPlace(activePiece.shape(), activePiece.rotation(), moved)) {
            activePiece = activePiece.movedTo(moved);
        }
        return noEvent();
    }

    private EngineStep rotate(boolean clockwise) {
        RotationResult result = rotationSystem.tryRotate(
                board, activePiece.shape(), activePiece.rotation(), activePiece.origin(), clockwise);
        if (result.rotated()) {
            activePiece = activePiece.rotatedTo(result.rotation(), result.origin());
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
        if (board.canPlace(activePiece.shape(), activePiece.rotation(), lowered)) {
            activePiece = activePiece.movedTo(lowered);
            return new EngineStep(snapshot(), new DropResult(1), null, null);
        }
        return lockActivePieceAndSpawnNext(null);
    }

    private EngineStep hardDrop() {
        Position position = activePiece.origin();
        int cellsDropped = 0;
        while (board.canPlace(activePiece.shape(), activePiece.rotation(), position.translate(0, 1))) {
            position = position.translate(0, 1);
            cellsDropped++;
        }
        activePiece = activePiece.movedTo(position);
        return lockActivePieceAndSpawnNext(new DropResult(cellsDropped));
    }

    private EngineStep lockActivePieceAndSpawnNext(DropResult dropResult) {
        // 상단 밖에 걸친 블록은 일부만 보드에 저장하지 않고 최종 위치를 유지한 채 종료한다.
        // 스폰과 회전이 항상 화면 안에서만 일어나므로 정상 진행에서는 닿지 않는 방어 코드다.
        for (Position cell : activePiece.cells()) {
            if (cell.y() < 0) {
                phase = EnginePhase.GAME_OVER;
                return new EngineStep(snapshot(), dropResult, null, null);
            }
        }
        LockResult lockResult = new LockResult(activePiece.shape(), activePiece.origin(), activePiece.rotation());
        board.place(activePiece);
        LockContext context = new LockContext();
        List<ItemActivation> activations = activateItems(context);
        ClearResult clearResult = board.clearRows(context.rowsToClear);
        if (!clearResult.isEmpty()) {
            source.onLinesCleared(clearResult.lineCount());
        }
        spawnNext();
        return new EngineStep(snapshot(), dropResult, lockResult, clearResult, activations);
    }

    /**
     * 방금 고정한 블록의 아이템을 칸 순서대로 발동한다. 아이템은 한 번 발동하고 소모되므로
     * 효과를 내기 전에 보드의 아이템 표시를 지운다.
     */
    private List<ItemActivation> activateItems(LockContext context) {
        List<ItemActivation> activations = new ArrayList<>();
        Position[] cells = activePiece.cells();
        for (int i = 0; i < cells.length; i++) {
            Item item = activePiece.itemAt(i);
            if (item == null) {
                continue;
            }
            board.removeItem(cells[i]);
            item.onLock(context, cells[i]);
            activations.add(new ItemActivation(item, cells[i]));
        }
        return activations;
    }

    /**
     * 다음 블록을 꺼내 스폰 위치에 놓을 수 있으면 RUNNING으로, 놓을 수 없으면(더 이상 쌓을
     * 공간이 없으면) GAME_OVER로 전환한다.
     */
    private void spawnNext() {
        Piece piece = source.next();
        Position origin = spawnOrigin(piece.shape());
        if (board.canPlace(piece.shape(), 0, origin)) {
            activePiece = new ActivePiece(piece, 0, origin);
            phase = EnginePhase.RUNNING;
        } else {
            activePiece = null;
            phase = EnginePhase.GAME_OVER;
        }
    }

    /**
     * shape의 스폰(회전0) 모양이 보드 가로 중앙에 오도록, 그리고 모든 회전 상태 중 가장 위쪽
     * 칸이 보드 0행에 오도록 origin을 계산한다. 블록별 상대좌표의 바운딩 박스가 서로 달라도
     * (예: O는 x=1~2, I는 x=0~3) 항상 같은 방식으로 중앙 정렬되도록 실제 바운딩 박스를
     * 계산해서 보정한다.
     *
     * <p>세로 위치를 회전0이 아니라 모든 회전 상태 기준으로 맞추기 때문에, 스폰 직후 어느
     * 방향으로 회전해도 칸이 보드 위로 잘려 나가지 않는다. 대신 회전0 모양의 맨 윗줄이 비어 있는
     * 블록(I/J/L)은 O/T/S/Z보다 한 줄 아래에서 시작한다.
     */
    private Position spawnOrigin(Shape shape) {
        Position[] cells = shape.cellsAt(0);
        int minX = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        for (Position cell : cells) {
            minX = Math.min(minX, cell.x());
            maxX = Math.max(maxX, cell.x());
        }
        int minY = Integer.MAX_VALUE;
        for (int rotation = 0; rotation < shape.rotationStates(); rotation++) {
            for (Position cell : shape.cellsAt(rotation)) {
                minY = Math.min(minY, cell.y());
            }
        }
        int boxWidth = maxX - minX + 1;
        int originX = (board.width() - boxWidth) / 2 - minX;
        int originY = -minY;
        return new Position(originX, originY);
    }

    private EngineStep noEvent() {
        return new EngineStep(snapshot(), null, null, null);
    }

    /** 고정 한 번 동안 아이템 효과가 쓰는 보드 조작. 지울 줄은 모아 두었다가 한 번에 지운다. */
    private final class LockContext implements ItemContext {
        private final Set<Integer> rowsToClear = new TreeSet<>();

        @Override
        public int width() {
            return board.width();
        }

        @Override
        public int height() {
            return board.height();
        }

        @Override
        public Cell cellAt(Position position) {
            return board.cellAt(position);
        }

        @Override
        public void clearRow(int row) {
            if (row < 0 || row >= board.height()) {
                throw new IllegalArgumentException("Row " + row + " is outside the board");
            }
            rowsToClear.add(row);
        }

        @Override
        public void removeCell(Position position) {
            board.removeCell(position);
        }
    }
}
