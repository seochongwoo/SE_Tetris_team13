package team.tetris.application.network;

import java.util.List;
import org.junit.jupiter.api.Test;
import team.tetris.application.*;
import team.tetris.core.*;
import team.tetris.core.item.*;
import team.tetris.core.rule.*;
import static org.junit.jupiter.api.Assertions.*;

class PlayerStatesTest {
    @Test void allShapesItemsAndSixCellWeightSurviveProjection() {
        for (var shape : PlayerState.ShapeCode.values()) for (var item : PlayerState.ItemCode.values()) {
            if (shape == PlayerState.ShapeCode.EMPTY && item != PlayerState.ItemCode.NONE) continue;
            var tile = new PlayerState.Tile(shape, item);
            assertEquals(tile, PlayerStates.tile(PlayerStates.cell(tile)));
        }
        var source = new PieceSource() {
            public Piece next() { return new WeightBlock().toItemPiece(Piece.of(TetrominoType.T), new java.util.Random(1)); }
            public Piece peek() { return next(); }
        };
        var session = new SinglePlayerSession(new PlayerEngine(10, 20, source), new ScorePolicy(), new SpeedPolicy());
        var state = PlayerStates.from(session.snapshot(), List.of(), false);
        assertEquals(6, state.active().size()); assertEquals(6, state.next().size());
        assertEquals(PlayerState.ItemCode.WEIGHT, state.next().getFirst().tile().item());
        assertEquals(1, state.level());
    }

    @Test void itemClearProducesSameAttackThroughP2pAndDoesNotTransferItemAttributes() {
        try (var p = new P2pMatchTestSupport.Pair(MatchMessage.Mode.ITEM, null, null)) {
            var board = P2pMatchTestSupport.twoLines();
            board[18][0] = Cell.withItem(TetrominoType.T, new DoubleScoreItem());
            var source = new PlainPieceSource(team.tetris.ui.TestApplication.constant(TetrominoType.O));
            var piece = Piece.of(TetrominoType.O).withItem(0, new LineClearItem());
            var session = new P2pMatch(p.ss, incoming -> new PlayerEngine(board,
                    new ActivePiece(piece, 0, new Position(3, 0)), source, incoming), new ScorePolicy(), new SpeedPolicy());
            session.handle(GameCommand.HARD_DROP); p.c.update(0);
            assertEquals(List.of(975, 975), p.c.local().pendingRows());
            assertEquals(0, p.c.local().doubleScoreClears());
            p.c.handle(GameCommand.HARD_DROP);
            for (int x = 0; x < 10; x++) assertEquals(PlayerState.ItemCode.NONE, p.c.local().board().get(190 + x).item());
            assertEquals(2, session.local().clearedLines());
            var during = session.local();
            session.update(200_000_000L);
            assertNotEquals(during.board(), session.local().board());
            session.update(100_000_000L);
            assertEquals(0, session.local().board().stream().filter(tile -> tile.shape() != PlayerState.ShapeCode.EMPTY).count());
        }
    }
}
