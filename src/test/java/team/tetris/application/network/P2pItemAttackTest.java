package team.tetris.application.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;
import team.tetris.application.GameCommand;
import team.tetris.application.ScorePolicy;
import team.tetris.application.SpeedPolicy;
import team.tetris.application.model.Difficulty;
import team.tetris.application.network.MatchMessage.Mode;
import team.tetris.core.ActivePiece;
import team.tetris.core.Cell;
import team.tetris.core.Piece;
import team.tetris.core.PlayerEngine;
import team.tetris.core.Position;
import team.tetris.core.TetrominoType;
import team.tetris.core.item.ItemCatalog;
import team.tetris.core.item.LineClearItem;
import team.tetris.core.rule.ItemPieceSource;
import team.tetris.application.PieceWeightPolicy;
import team.tetris.core.rule.PlainPieceSource;
import team.tetris.core.rule.WeightedRandomGenerator;

/**
 * P2P 아이템 대전도 로컬 대전과 같은 공격 규칙을 쓴다 (3차 카드 21, 안건 ⑭·㉔). 줄 삭제 아이템(L)으로 만든
 * 공격 줄이 빈칸과 빈 줄까지 그대로 상대의 대기열에 도착하는지 확인한다.
 */
class P2pItemAttackTest {

    private static final int WIDTH = PlayerState.WIDTH;
    private static final int HEIGHT = PlayerState.HEIGHT;

    private static Cell[][] board(boolean bottomRowOpenAt45) {
        Cell[][] board = new Cell[HEIGHT][WIDTH];
        for (Cell[] row : board) {
            Arrays.fill(row, Cell.EMPTY);
        }
        if (bottomRowOpenAt45) {
            Arrays.fill(board[HEIGHT - 1], Cell.occupiedBy(TetrominoType.T));
            board[HEIGHT - 1][4] = Cell.EMPTY;
            board[HEIGHT - 1][5] = Cell.EMPTY;
        }
        return board;
    }

    /** O의 윗줄 칸 하나에 L을 실은 블록. */
    private static Piece oWithLineClear() {
        Position[] cells = TetrominoType.O.cellsAt(0);
        int top = 0;
        for (int i = 1; i < cells.length; i++) {
            if (cells[i].y() < cells[top].y()) {
                top = i;
            }
        }
        return Piece.of(TetrominoType.O).withItem(top, new LineClearItem());
    }

    private static P2pLobby lobby(LobbyTestSupport.Transport transport) {
        return new P2pLobby(() -> transport, List::of, new RecentIps(new LobbyTestSupport.MemoryIps()),
                Difficulty.NORMAL, () -> 42);
    }

    /** 실제 대전과 같은 아이템 공급자(블록 순서는 같은 시드, 아이템은 각자)로 엔진을 만든다. */
    private static P2pMatch match(P2pLobby.StartedMatch started, Cell[][] board, Piece first) {
        return new P2pMatch(started, incoming -> {
            var pieces = new ItemPieceSource(new PlainPieceSource(new WeightedRandomGenerator(
                    PieceWeightPolicy.weightsFor(started.configuration().difficulty()), new Random(started.pieceSeed()))),
                    ItemCatalog.all(), new Random(started.connection().role().ordinal()));
            return new PlayerEngine(board, new ActivePiece(first, 0, new Position(3, 0)), pieces, incoming);
        }, new ScorePolicy(), new SpeedPolicy());
    }

    @Test
    void lineClearItemAttacksArriveWithTheSameShapeAsInALocalBattle() {
        var st = new LobbyTestSupport.Transport();
        var ct = new LobbyTestSupport.Transport();
        st.peer = ct;
        ct.peer = st;
        var server = lobby(st);
        var client = lobby(ct);
        server.host();
        client.connect("127.0.0.1");
        st.connected();
        ct.connected();
        server.update();
        client.update();
        server.configure(Mode.ITEM, 0);
        client.update();
        server.ready();
        client.ready();
        server.update();
        client.update();
        var s = match(server.takeStartedMatch().orElseThrow(), board(true), oWithLineClear());
        var c = match(client.takeStartedMatch().orElseThrow(), board(false), Piece.of(TetrominoType.O));
        s.update(0);
        c.update(0);

        // 서버: 맨 아래 꽉 찬 줄 + L로 지운 윗줄(O 칸만 있던 줄) = 2줄 삭제.
        s.handle(GameCommand.HARD_DROP);
        c.update(0);

        // 로컬 대전(ItemBattleTest)과 같은 모양: 빈 줄, 그리고 4·5열이 빈 공격 줄. 비트 x는 x열의 공격 칸이다.
        int allButFourAndFive = (1 << WIDTH) - 1 & ~(1 << 4) & ~(1 << 5);
        assertEquals(List.of(0, allButFourAndFive), c.local().pendingRows());
        assertTrue(c.result().isEmpty() && c.failure().isEmpty());

        // 클라이언트가 다음 블록을 고정하면 받은 모양 그대로 보드 아래에 들어간다.
        c.handle(GameCommand.HARD_DROP);
        c.update(0);
        var tiles = c.local().board();
        for (int x = 0; x < WIDTH; x++) {
            boolean attackCell = x != 4 && x != 5;
            assertEquals(attackCell ? PlayerState.ShapeCode.ATTACK : PlayerState.ShapeCode.EMPTY,
                    tiles.get((HEIGHT - 1) * WIDTH + x).shape(), "맨 아래 줄 " + x + "열");
            assertEquals(PlayerState.ShapeCode.EMPTY, tiles.get((HEIGHT - 2) * WIDTH + x).shape(), "빈 줄 " + x + "열");
            assertEquals(PlayerState.ItemCode.NONE, tiles.get((HEIGHT - 1) * WIDTH + x).item(), "공격 줄에 아이템 없음");
        }
        server.close();
        client.close();
    }
}
