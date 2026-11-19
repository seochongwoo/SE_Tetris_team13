package team.tetris.application.network;

import java.util.ArrayList;
import java.util.List;
import team.tetris.application.GameSnapshot;
import team.tetris.core.*;
import team.tetris.core.item.*;
import team.tetris.application.network.PlayerState.*;

/** 엔진 상태를 전송용 화면으로 투영한다. 원격 보드를 다시 실행하지 않는다. */
public final class PlayerStates {
    private PlayerStates() {}

    public static PlayerState from(GameSnapshot snapshot, List<List<Cell>> pending, boolean finished) {
        var engine = snapshot.engine();
        var animation = finished ? null : snapshot.lineClearAnimation();
        Cell[][] cells = animation == null ? engine.board() : animation.boardBeforeClear(engine.board());
        var board = new ArrayList<Tile>();
        for (var row : cells) for (var cell : row) board.add(tile(cell));
        if (animation != null) {
            // 전송 화면도 삭제 연출의 진행을 반영한다. 삭제 대상 외의 칸은 그대로 보존한다.
            var targets = new java.util.TreeMap<Integer, java.util.BitSet>();
            for (var row : animation.rows()) targets.computeIfAbsent(row.rowIndex(), k -> new java.util.BitSet()).set(0, 10);
            for (var cell : animation.removedCells()) targets.computeIfAbsent(cell.position().y(), k -> new java.util.BitSet()).set(cell.position().x());
            for (var entry : targets.entrySet()) {
                var columns = entry.getValue();
                int left = columns.nextSetBit(0), right = columns.length() - 1;
                int rings = (right - left + 2) / 2;
                long wipe = animation.elapsedNanos() - team.tetris.application.LineClearAnimation.FLASH_NANOS;
                int removed = 1 + (int) (wipe * rings / (team.tetris.application.LineClearAnimation.DURATION_NANOS
                        - team.tetris.application.LineClearAnimation.FLASH_NANOS));
                for (int x = left; x >= 0; x = columns.nextSetBit(x + 1))
                    if (!animation.flashing() && Math.abs(2 * x - left - right) / 2 < removed)
                        board.set(entry.getKey() * 10 + x, tile(Cell.EMPTY));
            }
        }
        ActivePiece active = animation == null ? engine.activePiece() : animation.activePiece();
        var placed = new ArrayList<PlacedTile>();
        if (active != null) {
            Position[] positions = active.cells();
            for (int i = 0; i < positions.length; i++) placed.add(new PlacedTile(positions[i].x(), positions[i].y(),
                    tile(new Cell(active.shape(), active.itemAt(i)))));
        }
        var next = new ArrayList<PlacedTile>();
        Piece piece = engine.nextPiece();
        Position[] positions = piece.shape().cellsAt(0);
        for (int i = 0; i < positions.length; i++) next.add(new PlacedTile(positions[i].x(), positions[i].y(),
                tile(new Cell(piece.shape(), piece.itemAt(i)))));
        return new PlayerState(board, placed, next, snapshot.score(), snapshot.level() + 1,
                snapshot.clearedLines(), snapshot.doubleScoreClears(), engine.phase() == EnginePhase.GAME_OVER, masks(pending));
    }

    public static List<Integer> masks(List<List<Cell>> rows) {
        return rows.stream().limit(10).map(row -> {
            int mask = 0;
            for (int x = 0; x < row.size(); x++) if (!row.get(x).isEmpty()) mask |= 1 << x;
            return mask;
        }).toList();
    }

    public static List<List<Cell>> rows(List<Integer> masks) {
        return masks.stream().map(mask -> {
            var row = new ArrayList<Cell>();
            for (int x = 0; x < 10; x++) row.add((mask & (1 << x)) == 0 ? Cell.EMPTY : Cell.occupiedBy(AttackBlock.INSTANCE));
            return List.copyOf(row);
        }).toList();
    }

    public static Tile tile(Cell cell) {
        Shape shape = cell.occupiedBy();
        ShapeCode code = shape == null ? ShapeCode.EMPTY : shape instanceof TetrominoType type ? ShapeCode.valueOf(type.name())
                : shape == WeightBlock.SHAPE ? ShapeCode.WEIGHT : shape == SoloBlock.SHAPE ? ShapeCode.SOLO
                : shape == AttackBlock.INSTANCE ? ShapeCode.ATTACK : ShapeCode.TEMPORARY;
        Item item = cell.item();
        ItemCode itemCode = item == null ? ItemCode.NONE : item instanceof LineClearItem ? ItemCode.LINE_CLEAR
                : item instanceof BombItem ? ItemCode.BOMB : item instanceof ChainClearItem ? ItemCode.CHAIN_CLEAR
                : item instanceof DoubleScoreItem ? ItemCode.DOUBLE_SCORE : ItemCode.WEIGHT;
        return new Tile(code, itemCode);
    }

    public static Cell cell(Tile tile) {
        Shape shape = switch (tile.shape()) {
            case EMPTY -> null;
            case WEIGHT -> WeightBlock.SHAPE;
            case SOLO -> SoloBlock.SHAPE;
            case ATTACK -> AttackBlock.INSTANCE;
            case TEMPORARY -> TemporaryBlock.INSTANCE;
            default -> TetrominoType.valueOf(tile.shape().name());
        };
        Item item = switch (tile.item()) {
            case NONE -> null;
            case LINE_CLEAR -> new LineClearItem();
            case BOMB -> new BombItem();
            case CHAIN_CLEAR -> new ChainClearItem();
            case DOUBLE_SCORE -> new DoubleScoreItem();
            case WEIGHT -> new WeightBlock();
        };
        return new Cell(shape, item);
    }
}
