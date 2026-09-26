package team.tetris.application;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import team.tetris.core.*;
import team.tetris.core.rule.PieceGenerator;

class SinglePlayerSessionIntegrationTest {
    private static SinglePlayerSession newGame() {
        PieceGenerator generator = new PieceGenerator() {
            public TetrominoType next() { return TetrominoType.O; }
            public TetrominoType peek() { return TetrominoType.O; }
        };
        return new SinglePlayerSession(new PlayerEngine(10, 20, generator));
    }

    @Test
    void realEnginePauseResumePreservesPieceAndRestartsGravityInterval() {
        GameSession session = newGame();
        session.update(500_000_000L);
        session.handle(GameCommand.PAUSE);
        ActivePiece paused = session.snapshot().engine().activePiece();
        session.update(Long.MAX_VALUE);
        session.handle(GameCommand.HARD_DROP);
        assertEquals(paused, session.snapshot().engine().activePiece());
        assertEquals(0, session.snapshot().score());
        assertEquals(EnginePhase.PAUSED, session.snapshot().engine().phase());
        session.handle(GameCommand.RESUME);
        session.update(999_999_999L);
        assertEquals(paused, session.snapshot().engine().activePiece());
        session.update(1);
        assertEquals(paused.origin().translate(0, 1), session.snapshot().engine().activePiece().origin());
        assertEquals(1, session.snapshot().score());
    }

    @Test
    void automaticSoftAndHardDropAwardSameDistanceScore() {
        GameSession automatic = newGame();
        GameSession soft = newGame();
        GameSession hard = newGame();
        for (int i = 0; i < 18; i++) {
            automatic.update(1_000_000_000L);
            soft.handle(GameCommand.SOFT_DROP);
        }
        hard.handle(GameCommand.HARD_DROP);
        assertEquals(18, automatic.snapshot().score());
        assertEquals(18, soft.snapshot().score());
        assertEquals(18, hard.snapshot().score());
        automatic.update(1_000_000_000L);
        soft.handle(GameCommand.SOFT_DROP);
        assertArrayEquals(hard.snapshot().engine().board(), automatic.snapshot().engine().board());
        assertArrayEquals(hard.snapshot().engine().board(), soft.snapshot().engine().board());
        assertEquals(18, soft.snapshot().score());
    }

    @Test
    void completesOneGameWithoutUiAndNewGameHasIndependentIdentity() {
        GameSession session = newGame();
        for (int i = 0; i < 20 && session.result().isEmpty(); i++) {
            session.handle(GameCommand.HARD_DROP);
        }
        GameResult result = session.result().orElseThrow();
        assertEquals(GameStatus.GAME_OVER, result.reason());
        assertEquals(90, result.score());
        assertEquals(0, result.clearedLines());
        GameSession next = newGame();
        assertEquals(GameStatus.RUNNING, next.snapshot().status());
        assertEquals(0, next.snapshot().score());
        next.handle(GameCommand.QUIT_GAME);
        assertNotEquals(result.gameId(), next.result().orElseThrow().gameId());
    }

    @Test
    void clearsRealRowsAndUpdatesBonus() {
        GameSession session = newGame();
        // O 블록 다섯 개를 바닥에 나란히 배치하여 두 줄 동시 삭제.
        for (int target : new int[]{0, 2, 4, 6, 8}) {
            for (int i = 0; i < 10; i++) session.handle(GameCommand.MOVE_LEFT);
            for (int i = 0; i < target; i++) session.handle(GameCommand.MOVE_RIGHT);
            session.handle(GameCommand.HARD_DROP);
        }
        assertEquals(2, session.snapshot().clearedLines());
        assertEquals(490, session.snapshot().score());
        for (Cell[] row : session.snapshot().engine().board()) {
            for (Cell cell : row) assertTrue(cell.isEmpty());
        }
    }
}
