package team.tetris.application.battle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;
import team.tetris.application.GameCommand;
import team.tetris.application.GameSnapshot;
import team.tetris.application.ScorePolicy;
import team.tetris.application.SpeedRule;
import team.tetris.application.model.Difficulty;
import team.tetris.application.model.GameMode;
import team.tetris.core.ActivePiece;
import team.tetris.core.AttackBlock;
import team.tetris.core.Cell;
import team.tetris.core.Piece;
import team.tetris.core.PlayerEngine;
import team.tetris.core.Position;
import team.tetris.core.TetrominoType;
import team.tetris.core.item.BombItem;
import team.tetris.core.item.ChainClearItem;
import team.tetris.core.item.DoubleScoreItem;
import team.tetris.core.item.Item;
import team.tetris.core.item.ItemCatalog;
import team.tetris.core.item.LineClearItem;
import team.tetris.core.item.SoloBlock;
import team.tetris.core.rule.ItemPieceSource;
import team.tetris.core.rule.PieceGenerator;
import team.tetris.core.rule.PieceSource;
import team.tetris.core.rule.PlainPieceSource;
import team.tetris.core.rule.SharedPieceSequence;

/**
 * 대전 아이템 모드 (3차 카드 21, R3-04, R3-18): 플레이어별 아이템과 아이템·공격의 관계를 대전 세션으로 확인한다.
 *
 * <ul>
 *   <li>줄 단위 삭제(꽉 찬 줄, L)만 상대에게 공격이 되고, 폭탄·연쇄 삭제는 자기 보드만 바꾼다 (안건 ⑭).</li>
 *   <li>아이템 효과(점수 2배, 아이템이 나오는 시점)는 플레이어마다 따로다.</li>
 *   <li>기본 블록 순서는 두 사람이 같고, 아이템 블록은 각자 지운 줄 수에 따라 나온다 (안건 ⑰).</li>
 * </ul>
 */
class ItemBattleTest {

    private static final int WIDTH = 10;
    private static final int HEIGHT = 8;
    private static final BattlePlayer ONE = BattlePlayer.ONE;
    private static final BattlePlayer TWO = BattlePlayer.TWO;
    private static final Cell ATTACK = Cell.occupiedBy(AttackBlock.INSTANCE);
    private static final Position O_START = new Position(3, 0);

    private static final SpeedRule SLOW = new SpeedRule() {
        @Override
        public int levelFor(int totalClearedLines) {
            return 0;
        }

        @Override
        public long gravityIntervalNanos(int level) {
            return 1_000_000_000L;
        }
    };

    private static PieceSource alwaysO() {
        return new PlainPieceSource(new PieceGenerator() {
            @Override
            public TetrominoType next() {
                return TetrominoType.O;
            }

            @Override
            public TetrominoType peek() {
                return TetrominoType.O;
            }
        });
    }

    private static Cell[][] empty() {
        Cell[][] board = new Cell[HEIGHT][WIDTH];
        for (Cell[] row : board) {
            Arrays.fill(row, Cell.EMPTY);
        }
        return board;
    }

    private static Cell[] row(Cell filler, int... holes) {
        Cell[] row = new Cell[WIDTH];
        Arrays.fill(row, filler);
        for (int hole : holes) {
            row[hole] = Cell.EMPTY;
        }
        return row;
    }

    /** 맨 아래 두 줄이 4·5열만 빈 보드. O가 떨어지면 두 줄이 꽉 찬다. */
    private static Cell[][] twoLines(Cell filler) {
        Cell[][] board = empty();
        board[HEIGHT - 2] = row(filler, 4, 5);
        board[HEIGHT - 1] = row(filler, 4, 5);
        return board;
    }

    private static List<Cell> attack(int... holes) {
        List<Cell> line = new ArrayList<>(Collections.nCopies(WIDTH, ATTACK));
        for (int hole : holes) {
            line.set(hole, Cell.EMPTY);
        }
        return line;
    }

    /** O의 윗줄 칸 하나에 item을 실은 블록. */
    private static Piece oWith(Item item) {
        Position[] cells = TetrominoType.O.cellsAt(0);
        int top = 0;
        for (int i = 1; i < cells.length; i++) {
            if (cells[i].y() < cells[top].y()) {
                top = i;
            }
        }
        return Piece.of(TetrominoType.O).withItem(top, item);
    }

    /** 두 플레이어의 보드와 첫 블록을 정해 아이템 대전을 만든다. 다음 블록은 모두 O다. */
    private static LocalBattleSession battle(Cell[][] one, Piece firstOne, Cell[][] two, Piece firstTwo) {
        return new LocalBattleSession(WIDTH, (player, incoming) -> new PlayerEngine(
                player == ONE ? one : two,
                start(player == ONE ? firstOne : firstTwo),
                alwaysO(), incoming), new ScorePolicy(), SLOW, GameMode.ITEM, Difficulty.NORMAL);
    }

    /** 1칸 블록(폭탄)은 4열, O는 4·5열에 떨어지게 둔다. */
    private static ActivePiece start(Piece piece) {
        return new ActivePiece(piece, 0, piece.shape() == SoloBlock.SHAPE ? new Position(4, 0) : O_START);
    }

    private static GameSnapshot game(LocalBattleSession session, BattlePlayer player) {
        return session.snapshot().of(player);
    }

    private static Cell[][] boardOf(LocalBattleSession session, BattlePlayer player) {
        return game(session, player).engine().board();
    }

    @Test
    void linesClearedWithTheLineClearItemReachTheOpponentWithTheirGaps() {
        // ONE: 맨 아래 줄만 4·5열이 비어 있고, O의 윗줄 칸에 L. 꽉 찬 줄 1개 + L 줄 1개 = 2줄 삭제 → 공격.
        Cell[][] one = empty();
        one[HEIGHT - 1] = row(Cell.occupiedBy(TetrominoType.T), 4, 5);
        LocalBattleSession session = battle(one, oWith(new LineClearItem()), empty(), Piece.of(TetrominoType.O));

        session.handle(ONE, GameCommand.HARD_DROP);

        assertEquals(List.of(attack(0, 1, 2, 3, 4, 5, 6, 7, 8, 9), attack(4, 5)), session.snapshot().pendingTwo());
        // TWO가 다음 블록을 고정하면 받은 모양 그대로 아래에 들어간다.
        session.handle(TWO, GameCommand.HARD_DROP);
        assertTrue(session.snapshot().pendingTwo().isEmpty());
        assertEquals(attack(4, 5), Arrays.asList(boardOf(session, TWO)[HEIGHT - 1]));
        assertEquals(attack(0, 1, 2, 3, 4, 5, 6, 7, 8, 9), Arrays.asList(boardOf(session, TWO)[HEIGHT - 2]));
    }

    @Test
    void aBombOrAChainClearOnlyChangesTheBoardOfThePlayerWhoUsedIt() {
        // 안건 ⑭: 칸 단위 삭제는 공격이 아니다. 상대 보드와 대기열은 그대로다.
        Cell[][] two = twoLines(Cell.occupiedBy(TetrominoType.Z));
        Cell[][] twoBefore = Arrays.stream(two).map(Cell[]::clone).toArray(Cell[][]::new);
        Piece bomb = new SoloBlock(new BombItem()).toItemPiece(Piece.of(TetrominoType.O), null);
        LocalBattleSession bombed = battle(twoLines(Cell.occupiedBy(TetrominoType.T)), bomb, two,
                Piece.of(TetrominoType.O));

        bombed.handle(ONE, GameCommand.HARD_DROP);

        assertTrue(bombed.snapshot().pendingTwo().isEmpty());
        assertTrue(Arrays.deepEquals(twoBefore, boardOf(bombed, TWO)));
        assertTrue(boardOf(bombed, ONE)[HEIGHT - 1][3].isEmpty(), "폭탄은 자기 보드의 칸만 지운다");

        LocalBattleSession chained = battle(twoLines(Cell.occupiedBy(TetrominoType.O)), oWith(new ChainClearItem()),
                twoLines(Cell.occupiedBy(TetrominoType.Z)), Piece.of(TetrominoType.O));
        chained.handle(ONE, GameCommand.HARD_DROP);

        assertTrue(chained.snapshot().pendingTwo().isEmpty(), "연쇄 삭제가 꽉 찬 줄을 없애 공격이 없다");
        assertEquals(0, game(chained, ONE).clearedLines());
        assertTrue(Arrays.deepEquals(twoBefore, boardOf(chained, TWO)));
    }

    @Test
    void theDoubleScoreItemOnlyBoostsThePlayerWhoUsedIt() {
        LocalBattleSession boosted = battle(twoLines(Cell.occupiedBy(TetrominoType.T)), oWith(new DoubleScoreItem()),
                twoLines(Cell.occupiedBy(TetrominoType.T)), Piece.of(TetrominoType.O));
        LocalBattleSession plain = battle(twoLines(Cell.occupiedBy(TetrominoType.T)), Piece.of(TetrominoType.O),
                twoLines(Cell.occupiedBy(TetrominoType.T)), Piece.of(TetrominoType.O));

        for (LocalBattleSession session : List.of(boosted, plain)) {
            session.handle(ONE, GameCommand.HARD_DROP);
            session.handle(TWO, GameCommand.HARD_DROP);
        }

        assertTrue(game(boosted, ONE).doubleScoreClears() > 0);
        assertEquals(0, game(boosted, TWO).doubleScoreClears());
        assertEquals(2 * game(plain, ONE).score(), game(boosted, ONE).score(), "같은 2줄 삭제의 점수가 2배");
        assertEquals(game(plain, TWO).score(), game(boosted, TWO).score(), "상대 점수는 영향을 받지 않는다");
        // 점수 2배도 공격은 일반 블록과 같다.
        assertEquals(plain.snapshot().pendingTwo(), boosted.snapshot().pendingTwo());
        assertEquals(plain.snapshot().pendingOne(), boosted.snapshot().pendingOne());
    }

    @Test
    void attackCellsAreNeverPartOfAChainAndStillCountForLines() {
        // 받은 공격 칸은 테트로미노가 아니라 연쇄 삭제 덩어리에 들지 않는다. 공격 칸으로 채운 줄도 꽉 차면
        // 지워져 다시 공격이 된다.
        LocalBattleSession session = battle(empty(), Piece.of(TetrominoType.O), twoLines(ATTACK),
                oWith(new ChainClearItem()));

        session.handle(TWO, GameCommand.HARD_DROP);

        assertEquals(2, game(session, TWO).clearedLines());
        assertEquals(List.of(attack(4, 5), attack(4, 5)), session.snapshot().pendingOne());
    }

    @Test
    void itemsFollowEachPlayersOwnClearsWhileThePlainPiecesStayShared() {
        // 안건 ⑰: 기본 블록은 같은 순서, 아이템 블록은 각자 지운 줄 수(10줄마다)에 따라 나온다.
        // 아이템 종류는 무작위라, 모양이 바뀌는 무게추·폭탄과 블록에 붙는 아이템이 모두 나오도록 시드를 바꿔 본다.
        java.util.Set<Object> seenShapes = new java.util.HashSet<>();
        for (int seed = 0; seed < 40; seed++) {
            seenShapes.add(checkSharedOrder(seed));
        }
        assertTrue(seenShapes.contains(team.tetris.core.item.WeightBlock.SHAPE), "무게추가 나온 경우");
        assertTrue(seenShapes.contains(SoloBlock.SHAPE), "폭탄이 나온 경우");
        assertTrue(seenShapes.stream().anyMatch(shape -> shape instanceof TetrominoType), "블록에 붙는 아이템");
    }

    /** ONE만 10줄을 지웠을 때 두 공급자를 20개씩 꺼내 비교한다. ONE의 아이템 블록 모양을 돌려준다. */
    private static Object checkSharedOrder(int seed) {
        TetrominoType[] cycle = TetrominoType.values();
        SharedPieceSequence sequence = new SharedPieceSequence(new PieceGenerator() {
            private int next;

            @Override
            public TetrominoType next() {
                return cycle[next++ % cycle.length];
            }

            @Override
            public TetrominoType peek() {
                return cycle[next % cycle.length];
            }
        });
        // 실제 조립(AppComposition)처럼 두 플레이어의 읽기 도구를 먼저 만든 뒤 각자 아이템 공급자로 감싼다.
        PieceGenerator readerOne = sequence.newReader();
        PieceGenerator readerTwo = sequence.newReader();
        ItemPieceSource one = new ItemPieceSource(new PlainPieceSource(readerOne), ItemCatalog.all(), new Random(seed));
        ItemPieceSource two = new ItemPieceSource(new PlainPieceSource(readerTwo), ItemCatalog.all(),
                new Random(seed + 1000));

        one.onLinesCleared(10);
        List<Piece> ones = new ArrayList<>();
        List<Piece> twos = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            ones.add(one.next());
            twos.add(two.next());
        }

        int itemPieces = 0;
        Object itemShape = null;
        for (int i = 0; i < ones.size(); i++) {
            Piece mine = ones.get(i);
            Piece theirs = twos.get(i);
            assertFalse(hasItem(theirs), "줄을 지우지 않은 TWO에게는 아이템이 없다");
            if (hasItem(mine)) {
                itemPieces++;
                itemShape = mine.shape();
            } else {
                // 무게추·폭탄은 원래 나올 블록 대신 나오지만 뒤의 순서를 밀지 않는다.
                assertEquals(theirs.shape(), mine.shape(), "시드 " + seed + ", 아이템이 아닌 블록은 같은 순서: " + i);
            }
        }
        assertEquals(1, itemPieces, "10줄에 아이템 1개");
        return itemShape;
    }

    private static boolean hasItem(Piece piece) {
        for (int i = 0; i < piece.shape().cellsAt(0).length; i++) {
            if (piece.itemAt(i) != null) {
                return true;
            }
        }
        return false;
    }
}
