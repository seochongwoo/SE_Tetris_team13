package team.tetris.core.rule;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import team.tetris.core.ActivePiece;
import team.tetris.core.AttackBlock;
import team.tetris.core.Cell;
import team.tetris.core.GameAction;
import team.tetris.core.PlayerEngine;
import team.tetris.core.Position;
import team.tetris.core.TetrominoType;
import team.tetris.core.result.EngineStep;

/** 공격 대기열: 최대 10줄, 아래에 추가, 가득 차면 무시, 넘치면 아래쪽 잘라내기 (3차 R3-14~17). */
class AttackQueueTest {

    private static final Cell A = Cell.occupiedBy(AttackBlock.INSTANCE);
    private static final int WIDTH = 10;

    /** hole 열만 빈 공격 줄. 줄마다 hole을 달리해 순서를 구분한다. */
    private static List<Cell> line(int hole) {
        List<Cell> line = new ArrayList<>(Collections.nCopies(WIDTH, A));
        line.set(hole, Cell.EMPTY);
        return line;
    }

    /** holes 순서대로 만든 공격 줄 묶음. */
    private static List<List<Cell>> lines(int... holes) {
        List<List<Cell>> result = new ArrayList<>();
        for (int hole : holes) {
            result.add(line(hole));
        }
        return result;
    }

    @Test
    void anEmptyQueueGivesNoLines() {
        AttackQueue queue = new AttackQueue(WIDTH);

        assertTrue(queue.take().isEmpty());
        assertEquals(0, queue.size());
    }

    @Test
    void newAttacksGoBelowTheWaitingLinesAndTakeEmptiesTheQueue() {
        // R3-15: 여러 번 공격으로 추가되는 줄은 이미 있는 줄의 아래쪽에 추가
        AttackQueue queue = new AttackQueue(WIDTH);

        assertEquals(2, queue.add(lines(0, 1)));
        assertEquals(3, queue.add(lines(2, 3, 4)));

        assertEquals(lines(0, 1, 2, 3, 4), queue.pending());
        assertEquals(5, queue.size());
        assertEquals(lines(0, 1, 2, 3, 4), queue.take());
        assertTrue(queue.take().isEmpty());
        assertEquals(0, queue.size());
    }

    @Test
    void eightWaitingLinesPlusThreeKeepTheEightAndTheTopTwoOfTheNewAttack() {
        // R3-14·R3-17: 합해서 10줄을 넘으면 제일 아래쪽을 잘라낸다.
        AttackQueue queue = new AttackQueue(WIDTH);
        queue.add(lines(0, 1, 2, 3, 4, 5, 6, 7));

        assertEquals(2, queue.add(lines(8, 9, 0)));

        assertEquals(lines(0, 1, 2, 3, 4, 5, 6, 7, 8, 9), queue.take());
    }

    @Test
    void attacksThatAddUpToExactlyTenLinesAreKeptWhole() {
        AttackQueue queue = new AttackQueue(WIDTH);
        queue.add(lines(0, 1, 2, 3, 4, 5, 6));

        assertEquals(3, queue.add(lines(7, 8, 9)));

        assertEquals(AttackQueue.MAX_LINES, queue.size());
        assertEquals(lines(0, 1, 2, 3, 4, 5, 6, 7, 8, 9), queue.pending());
    }

    @Test
    void aFullQueueIgnoresNewAttacks() {
        // R3-16: 이미 10줄이면 줄을 지워도 넘어가지 않는다.
        AttackQueue queue = new AttackQueue(WIDTH);
        queue.add(lines(0, 1, 2, 3, 4, 5, 6, 7, 8, 9));

        assertEquals(0, queue.add(lines(5, 5)));

        assertEquals(lines(0, 1, 2, 3, 4, 5, 6, 7, 8, 9), queue.take());
    }

    @Test
    void aSingleAttackLargerThanTheQueueKeepsItsTopTenLines() {
        AttackQueue queue = new AttackQueue(WIDTH);

        assertEquals(10, queue.add(lines(0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 0, 1)));

        assertEquals(lines(0, 1, 2, 3, 4, 5, 6, 7, 8, 9), queue.take());
    }

    @Test
    void theQueueKeepsItsOwnCopyOfTheLines() {
        AttackQueue queue = new AttackQueue(WIDTH);
        List<List<Cell>> attack = lines(3);
        List<List<Cell>> mutable = new ArrayList<>(List.of(new ArrayList<>(attack.getFirst())));
        queue.add(mutable);

        mutable.getFirst().set(0, Cell.EMPTY);
        mutable.add(line(4));

        assertEquals(attack, queue.pending());
        assertThrows(UnsupportedOperationException.class, () -> queue.pending().add(line(1)));
        assertThrows(NullPointerException.class, () -> queue.add(null));
        assertThrows(NullPointerException.class, () -> queue.add(Collections.singletonList(null)));
        assertEquals(attack, queue.take());
    }

    @Test
    void invalidLinesAreRejectedWholeBeforeAnythingIsQueued() {
        AttackQueue queue = new AttackQueue(WIDTH);
        queue.add(lines(0));
        List<Cell> full = Collections.nCopies(WIDTH, A);
        List<Cell> narrow = line(0).subList(0, WIDTH - 1);

        assertThrows(IllegalArgumentException.class, () -> queue.add(List.of(line(1), full)));
        assertThrows(IllegalArgumentException.class, () -> queue.add(List.of(line(1), narrow)));
        assertThrows(NullPointerException.class, () -> queue.add(List.of(java.util.Arrays.asList(new Cell[WIDTH]))));
        assertThrows(IllegalArgumentException.class, () -> new AttackQueue(0));
        assertEquals(lines(0), queue.take());
    }

    @Test
    void theEngineReceivesTheWaitingLinesWhenItsPieceLocks() {
        // 카드 15의 연결 지점: 대기열을 IncomingLines로 넘기면 블록 고정 때 한꺼번에 들어온다.
        AttackQueue queue = new AttackQueue(WIDTH);
        Cell[][] board = new Cell[8][WIDTH];
        for (Cell[] row : board) {
            java.util.Arrays.fill(row, Cell.EMPTY);
        }
        PieceSource source = new PlainPieceSource(new PieceGenerator() {
            @Override
            public TetrominoType next() {
                return TetrominoType.O;
            }

            @Override
            public TetrominoType peek() {
                return TetrominoType.O;
            }
        });
        PlayerEngine engine = new PlayerEngine(board, new ActivePiece(TetrominoType.O, 0, new Position(3, 0)),
                source, queue);
        queue.add(lines(0, 9));

        engine.apply(GameAction.MOVE_LEFT);
        assertEquals(2, queue.size());
        EngineStep step = engine.apply(GameAction.HARD_DROP);

        assertEquals(2, step.addedLines());
        assertEquals(0, queue.size());
        assertEquals(line(0), List.of(step.snapshot().board()[6]));
        assertEquals(line(9), List.of(step.snapshot().board()[7]));
    }

    @Test
    void concurrentAttacksNeverExceedTheLimitOrLoseAcceptedLines() throws Exception {
        // P2P에서는 수신 스레드가 공격을 넣고 게임 루프가 꺼내 간다.
        AttackQueue queue = new AttackQueue(WIDTH);
        AtomicInteger accepted = new AtomicInteger();
        AtomicInteger taken = new AtomicInteger();
        int writers = 4;
        int attacksPerWriter = 2_000;
        ExecutorService pool = Executors.newFixedThreadPool(writers + 1);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<?>> tasks = new ArrayList<>();
            for (int w = 0; w < writers; w++) {
                tasks.add(pool.submit(() -> {
                    start.await();
                    for (int i = 0; i < attacksPerWriter; i++) {
                        accepted.addAndGet(queue.add(lines(i % WIDTH, (i + 1) % WIDTH, (i + 2) % WIDTH)));
                    }
                    return null;
                }));
            }
            tasks.add(pool.submit(() -> {
                start.await();
                for (int i = 0; i < attacksPerWriter; i++) {
                    List<List<Cell>> batch = queue.take();
                    assertTrue(batch.size() <= AttackQueue.MAX_LINES);
                    taken.addAndGet(batch.size());
                }
                return null;
            }));
            start.countDown();
            for (Future<?> task : tasks) {
                task.get(30, TimeUnit.SECONDS);
            }
        } finally {
            pool.shutdownNow();
        }

        assertTrue(queue.size() <= AttackQueue.MAX_LINES);
        assertEquals(accepted.get(), taken.get() + queue.take().size());
    }
}
