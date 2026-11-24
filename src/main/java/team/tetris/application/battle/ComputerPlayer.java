package team.tetris.application.battle;

import java.util.Optional;
import team.tetris.application.GameCommand;
import team.tetris.application.model.Difficulty;
import team.tetris.core.ActivePiece;
import team.tetris.core.Board;
import team.tetris.core.Cell;
import team.tetris.core.EnginePhase;
import team.tetris.core.EngineSnapshot;
import team.tetris.core.Position;
import team.tetris.core.rule.DropRule;
import team.tetris.core.rule.RotationResult;
import team.tetris.core.rule.RotationSystem;

/**
 * 컴퓨터 대전의 상대 (도전 과제 CH-1). 현재 보드 상태를 받아 다음에 누를 키 하나를 돌려준다. 화면이 이 함수를
 * {@link #intervalNanos 일정 간격}으로 불러 사람이 키를 누르듯 한 번에 한 조작씩 넣는다.
 *
 * <p>상태를 저장하지 않는다. 부를 때마다 지금 블록을 놓을 수 있는 모든 회전·열을 찾아 놓은 뒤의 보드를
 * 평가하고, 가장 좋은 자리로 가는 첫 키를 고른다. 같은 보드와 블록이면 늘 같은 자리를 고르므로, 회전하거나
 * 옮긴 뒤 다시 불러도 목표가 바뀌지 않는다.
 *
 * <ul>
 *   <li>자리 평가는 놓은 뒤 꽉 찬 줄을 지운 보드로 한다. 지운 줄은 더하고, 쌓인 높이·막힌 빈칸·이웃 열의
 *       높이 차이는 뺀다.</li>
 *   <li>도달 가능 여부는 엔진과 같은 회전 규칙({@link RotationSystem})과 충돌 판정으로 확인한다.
 *       회전한 뒤 지금 높이에서 옆으로 막힘 없이 밀 수 있는 열만 후보로 삼는다.</li>
 *   <li>아이템 블록(무게추, 1칸 폭탄 등)도 같은 낙하 규칙({@link DropRule})으로 착지 자리를 계산한다.</li>
 * </ul>
 */
public final class ComputerPlayer {

    /** 놓은 뒤 보드 평가의 가중치. 지운 줄은 득, 높이·빈칸·울퉁불퉁함은 실이다. */
    static final double LINES_WEIGHT = 0.76;
    static final double HEIGHT_WEIGHT = -0.51;
    static final double HOLES_WEIGHT = -0.36;
    static final double BUMPINESS_WEIGHT = -0.18;
    /** 블록 일부가 보드 위로 삐져나오는 자리. 그대로 지는 자리라 다른 어떤 자리보다 나쁘게 본다. */
    private static final double OVERFLOW_PENALTY = -1_000_000;

    private final RotationSystem rotations = new RotationSystem();

    /**
     * 컴퓨터가 키를 누르는 간격. 요구사항의 0.5~1초 안에서 난이도가 높을수록 빠르게 누른다.
     */
    public static long intervalNanos(Difficulty difficulty) {
        return switch (difficulty) {
            case EASY -> 1_000_000_000L;
            case NORMAL -> 750_000_000L;
            case HARD -> 500_000_000L;
        };
    }

    /** 다음에 누를 키. 떨어지는 블록이 없으면(진행 중이 아니면) empty. */
    public Optional<GameCommand> nextCommand(EngineSnapshot engine) {
        ActivePiece active = engine.activePiece();
        if (engine.phase() != EnginePhase.RUNNING || active == null) {
            return Optional.empty();
        }
        Target target = bestTarget(engine.board(), active);
        if (target == null || target.score() <= OVERFLOW_PENALTY) {
            // 갈 곳이 없거나 어디에 놓아도 지는 자리면 더 옮기지 않고 떨어뜨린다.
            return Optional.of(GameCommand.HARD_DROP);
        }
        if (target.rotations() > 0) {
            return Optional.of(GameCommand.ROTATE_CW);
        }
        int dx = target.x() - active.origin().x();
        if (dx != 0) {
            return Optional.of(dx < 0 ? GameCommand.MOVE_LEFT : GameCommand.MOVE_RIGHT);
        }
        return Optional.of(GameCommand.HARD_DROP);
    }

    /** 가고 싶은 자리: 지금부터 시계 방향으로 몇 번 돌리고, 돌린 뒤 origin을 어느 열로 옮길지. */
    record Target(int rotations, int x, double score) {
    }

    /**
     * 갈 수 있는 자리 중 평가가 가장 좋은 곳. 점수가 같으면 회전 상태 번호가 작은 쪽, 그다음 왼쪽 열을 고른다.
     * 지금 회전 상태와 상관없이 같은 순서로 비교하므로 돌린 뒤 다시 불러도 같은 자리를 고른다.
     */
    Target bestTarget(Cell[][] cells, ActivePiece active) {
        Board board = new Board(cells);
        int states = Math.max(1, active.shape().rotationStates());
        Target best = null;
        int bestRotation = Integer.MAX_VALUE;
        ActivePiece rotated = active;
        for (int turns = 0; turns < states; turns++) {
            if (turns > 0) {
                RotationResult result = rotations.tryRotate(board, rotated.shape(), rotated.rotation(),
                        rotated.origin(), true);
                if (!result.rotated()) {
                    break; // 이 회전이 막히면 그 뒤 회전도 지금 자리에서는 할 수 없다.
                }
                rotated = rotated.rotatedTo(result.rotation(), result.origin());
            }
            for (ActivePiece slid : reachableColumns(board, rotated)) {
                double score = evaluate(cells, slid);
                int rotation = slid.rotation();
                boolean better = best == null || score > best.score()
                        || (score == best.score() && (rotation < bestRotation
                        || (rotation == bestRotation && slid.origin().x() < best.x())));
                if (better) {
                    best = new Target(turns, slid.origin().x(), score);
                    bestRotation = rotation;
                }
            }
        }
        return best;
    }

    /** 지금 높이에서 왼쪽·오른쪽으로 막힐 때까지 밀어 본 모든 위치 (자기 자리 포함). */
    private static java.util.List<ActivePiece> reachableColumns(Board board, ActivePiece piece) {
        java.util.List<ActivePiece> reachable = new java.util.ArrayList<>();
        reachable.add(piece);
        for (int step : new int[] {-1, 1}) {
            ActivePiece current = piece;
            while (true) {
                ActivePiece next = current.movedTo(current.origin().translate(step, 0));
                if (!board.canPlace(next.shape(), next.rotation(), next.origin())) {
                    break;
                }
                reachable.add(next);
                current = next;
            }
        }
        return reachable;
    }

    /** piece를 그 자리에서 끝까지 떨어뜨려 놓고 꽉 찬 줄을 지운 보드의 평가 점수. 원래 보드는 바꾸지 않는다. */
    static double evaluate(Cell[][] cells, ActivePiece piece) {
        Board board = new Board(cells);
        ActivePiece landing = piece;
        ActivePiece next;
        while ((next = DropRule.descend(board, landing)) != null) {
            landing = next;
        }
        for (Position cell : landing.cells()) {
            if (cell.y() < 0) {
                return OVERFLOW_PENALTY;
            }
        }
        board.place(landing);
        int lines = board.clearFullLines().lineCount();
        int[] heights = columnHeights(board);
        int aggregate = 0;
        int bumpiness = 0;
        for (int x = 0; x < heights.length; x++) {
            aggregate += heights[x];
            if (x > 0) {
                bumpiness += Math.abs(heights[x] - heights[x - 1]);
            }
        }
        return LINES_WEIGHT * lines + HEIGHT_WEIGHT * aggregate + HOLES_WEIGHT * holes(board, heights)
                + BUMPINESS_WEIGHT * bumpiness;
    }

    /** 열마다 가장 위에 있는 칸의 높이 (바닥이 0). */
    static int[] columnHeights(Board board) {
        int[] heights = new int[board.width()];
        for (int x = 0; x < board.width(); x++) {
            for (int y = 0; y < board.height(); y++) {
                if (!board.cellAt(new Position(x, y)).isEmpty()) {
                    heights[x] = board.height() - y;
                    break;
                }
            }
        }
        return heights;
    }

    /** 위에 칸이 있어 막힌 빈칸 수. */
    static int holes(Board board, int[] heights) {
        int holes = 0;
        for (int x = 0; x < board.width(); x++) {
            for (int y = board.height() - heights[x]; y < board.height(); y++) {
                if (board.cellAt(new Position(x, y)).isEmpty()) {
                    holes++;
                }
            }
        }
        return holes;
    }
}
