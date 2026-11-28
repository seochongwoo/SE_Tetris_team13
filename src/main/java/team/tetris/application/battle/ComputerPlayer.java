package team.tetris.application.battle;

import java.util.Optional;
import team.tetris.application.GameCommand;
import team.tetris.application.model.Difficulty;
import team.tetris.core.ActivePiece;
import team.tetris.core.Board;
import team.tetris.core.Cell;
import team.tetris.core.EnginePhase;
import team.tetris.core.EngineSnapshot;
import team.tetris.core.Piece;
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
 *   <li>어려움 난이도({@link #forDifficulty})는 NEXT 블록까지 본다. 지금 블록의 자리마다 다음 블록을 가장
 *       좋게 놓았을 때의 보드로 평가하므로, 다음 블록이 들어갈 자리를 남겨 두는 수를 고를 수 있다. 다음
 *       블록은 아직 나오지 않았으므로 위에서 바로 내려놓을 수 있는 회전·열만 따진다.</li>
 *   <li>쉬움·보통은 사람처럼 가끔 실수한다. 블록마다 정해진 확률로 가장 좋은 자리 대신 그다음 몇 등 중 하나를
 *       고른다. 실수 여부와 고를 자리는 보드와 블록 모양으로 정하므로, 블록 하나가 떨어지는 동안에는 몇 번을
 *       불러도 같은 자리로 간다.</li>
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
    /** true면 NEXT 블록까지 놓아 보고 평가한다. */
    private final boolean lookAhead;
    /** 블록마다 가장 좋은 자리 대신 다른 자리를 고를 확률 (0이면 실수하지 않는다). */
    private final double mistakeRate;
    /** 실수할 때 고르는 자리: 2등부터 이 등수까지 중 하나. */
    private final int mistakeRank;

    /** 실수하지 않고 지금 블록만 보는 컴퓨터. */
    public ComputerPlayer() {
        this(false);
    }

    /** 실수하지 않는 컴퓨터. @param lookAhead true면 NEXT 블록까지 본다 */
    public ComputerPlayer(boolean lookAhead) {
        this(lookAhead, 0, 1);
    }

    /**
     * @param lookAhead   true면 NEXT 블록까지 본다
     * @param mistakeRate 블록마다 가장 좋은 자리 대신 다른 자리를 고를 확률 (0~1)
     * @param mistakeRank 실수할 때 2등부터 이 등수까지 중 하나를 고른다 (1이면 실수하지 않는다)
     */
    public ComputerPlayer(boolean lookAhead, double mistakeRate, int mistakeRank) {
        if (mistakeRate < 0 || mistakeRate > 1 || mistakeRank < 1) {
            throw new IllegalArgumentException("Invalid mistakes: " + mistakeRate + ", " + mistakeRank);
        }
        this.lookAhead = lookAhead;
        this.mistakeRate = mistakeRate;
        this.mistakeRank = mistakeRank;
    }

    /**
     * 난이도에 맞는 컴퓨터. 쉬움은 블록의 35%를 2~4등 자리에, 보통은 15%를 2~3등 자리에 놓는다. 어려움은
     * 실수하지 않고 NEXT 블록까지 본다.
     */
    public static ComputerPlayer forDifficulty(Difficulty difficulty) {
        return switch (difficulty) {
            case EASY -> new ComputerPlayer(false, 0.35, 4);
            case NORMAL -> new ComputerPlayer(false, 0.15, 3);
            case HARD -> new ComputerPlayer(true);
        };
    }

    public double mistakeRate() {
        return mistakeRate;
    }

    public boolean looksAhead() {
        return lookAhead;
    }

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
        Target target = chooseTarget(engine.board(), active, lookAhead ? engine.nextPiece() : null);
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

    /**
     * 가고 싶은 자리: 지금부터 시계 방향으로 몇 번 돌리고, 돌린 뒤 origin을 어느 열로 옮길지.
     * rotation은 그 자리의 회전 상태 번호로, 지금 회전 상태와 상관없이 자리끼리 줄 세울 때 쓴다.
     */
    record Target(int rotations, int rotation, int x, double score) {
        /** 점수가 높은 순, 같으면 회전 상태 번호가 작은 순, 그다음 왼쪽 열 순. */
        static final java.util.Comparator<Target> ORDER = java.util.Comparator.comparingDouble(Target::score)
                .reversed().thenComparingInt(Target::rotation).thenComparingInt(Target::x);
    }

    /**
     * 실제로 갈 자리. 실수하지 않으면 1등 자리이고, 실수하면 점수가 다른 자리들 중 2등부터
     * {@link #mistakeRank}등까지 하나를 고른다. 지는 자리는 고르지 않는다. 실수 여부는 보드의 칸 배치와
     * 블록 모양으로 정해, 같은 블록이 떨어지는 동안에는 늘 같은 결과가 나온다.
     */
    Target chooseTarget(Cell[][] cells, ActivePiece active, Piece next) {
        java.util.List<Target> ranked = ranked(cells, active, next);
        if (ranked.isEmpty()) {
            return null;
        }
        if (mistakeRate == 0 || mistakeRank < 2) {
            return ranked.get(0);
        }
        java.util.List<Target> distinct = new java.util.ArrayList<>();
        for (Target target : ranked) {
            if (target.score() > OVERFLOW_PENALTY
                    && (distinct.isEmpty() || distinct.get(distinct.size() - 1).score() != target.score())) {
                distinct.add(target);
            }
        }
        java.util.Random dice = new java.util.Random(seedOf(cells, active));
        if (distinct.size() < 2 || dice.nextDouble() >= mistakeRate) {
            return ranked.get(0);
        }
        int choices = Math.min(mistakeRank, distinct.size()) - 1;
        return distinct.get(1 + dice.nextInt(choices));
    }

    /** 보드의 칸 배치와 블록 모양이 같으면 같은 값. 블록이 움직이거나 돌아도 바뀌지 않는다. */
    static long seedOf(Cell[][] cells, ActivePiece active) {
        long seed = active.shape().toString().hashCode();
        for (Cell[] row : cells) {
            for (Cell cell : row) {
                seed = seed * 31 + (cell.isEmpty() ? 0 : 1);
            }
        }
        return seed;
    }

    /**
     * 갈 수 있는 자리 중 평가가 가장 좋은 곳. 점수가 같으면 회전 상태 번호가 작은 쪽, 그다음 왼쪽 열을 고른다.
     * 지금 회전 상태와 상관없이 같은 순서로 비교하므로 돌린 뒤 다시 불러도 같은 자리를 고른다.
     */
    Target bestTarget(Cell[][] cells, ActivePiece active) {
        return bestTarget(cells, active, null);
    }

    /** @param next null이 아니면 이 블록을 다음에 가장 좋게 놓은 보드까지 보고 평가한다 */
    Target bestTarget(Cell[][] cells, ActivePiece active, Piece next) {
        java.util.List<Target> ranked = ranked(cells, active, next);
        return ranked.isEmpty() ? null : ranked.get(0);
    }

    /** 갈 수 있는 모든 자리를 {@link Target#ORDER} 순서로. */
    java.util.List<Target> ranked(Cell[][] cells, ActivePiece active, Piece next) {
        Board board = new Board(cells);
        int states = Math.max(1, active.shape().rotationStates());
        java.util.List<Target> targets = new java.util.ArrayList<>();
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
                double score = next == null ? evaluate(cells, slid) : evaluateWithNext(cells, slid, next);
                targets.add(new Target(turns, slid.rotation(), slid.origin().x(), score));
            }
        }
        targets.sort(Target.ORDER);
        return targets;
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
        Settled settled = settle(cells, piece);
        return settled == null ? OVERFLOW_PENALTY : score(settled.board(), settled.lines());
    }

    /**
     * piece를 놓은 뒤, next를 위에서 바로 내려놓을 수 있는 자리 중 가장 좋은 곳에 놓은 보드의 평가 점수.
     * 두 블록이 지운 줄을 합쳐 센다. next를 놓을 곳이 없으면 지는 자리로 본다.
     */
    static double evaluateWithNext(Cell[][] cells, ActivePiece piece, Piece next) {
        Settled first = settle(cells, piece);
        if (first == null) {
            return OVERFLOW_PENALTY;
        }
        Cell[][] afterFirst = first.board().snapshot();
        double best = OVERFLOW_PENALTY;
        int states = Math.max(1, next.shape().rotationStates());
        for (int rotation = 0; rotation < states; rotation++) {
            int top = 0;
            int minX = Integer.MAX_VALUE;
            int maxX = Integer.MIN_VALUE;
            for (Position offset : next.shape().cellsAt(rotation)) {
                top = Math.min(top, offset.y());
                minX = Math.min(minX, offset.x());
                maxX = Math.max(maxX, offset.x());
            }
            for (int x = -minX; x + maxX < first.board().width(); x++) {
                ActivePiece candidate = new ActivePiece(next, rotation, new Position(x, -top));
                if (!first.board().canPlace(candidate.shape(), rotation, candidate.origin())) {
                    continue;
                }
                Settled second = settle(afterFirst, candidate);
                if (second != null) {
                    best = Math.max(best, score(second.board(), first.lines() + second.lines()));
                }
            }
        }
        return best;
    }

    /** 블록을 놓고 줄을 지운 보드와 지운 줄 수. */
    private record Settled(Board board, int lines) {
    }

    /** piece를 끝까지 떨어뜨려 놓고 꽉 찬 줄을 지운다. 블록이 보드 위로 삐져나오면 null. */
    private static Settled settle(Cell[][] cells, ActivePiece piece) {
        Board board = new Board(cells);
        if (!board.canPlace(piece.shape(), piece.rotation(), piece.origin())) {
            return null; // 이미 쌓인 칸과 겹친 자리: 놓을 수 없다
        }
        ActivePiece landing = piece;
        ActivePiece next;
        while ((next = DropRule.descend(board, landing)) != null) {
            landing = next;
        }
        for (Position cell : landing.cells()) {
            if (cell.y() < 0) {
                return null;
            }
        }
        board.place(landing);
        return new Settled(board, board.clearFullLines().lineCount());
    }

    /** 놓은 뒤 보드의 점수: 지운 줄은 더하고 높이·막힌 빈칸·울퉁불퉁함은 뺀다. */
    private static double score(Board board, int lines) {
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
