package team.tetris.performance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static team.tetris.ui.TestApplication.FRAME;

import org.junit.jupiter.api.Test;
import team.tetris.core.ActivePiece;
import team.tetris.core.TetrominoType;
import team.tetris.ui.ScreenRouter;
import team.tetris.ui.TestApplication;
import team.tetris.ui.screen.GameScreen;

/**
 * 비기능 요구사항 검증: "반복된 키 입력이 무시되지 않고 블럭이 즉시, 정확하게 반응해야 함"과
 * "플레이에 문제가 없을 정도의 성능".
 *
 * <p>시간 기준은 실행 환경 최소 사양(1.2GHz, 1GB)을 고려해 한 프레임(16ms)보다 훨씬 낮게 잡되,
 * CI 머신의 변동에 흔들리지 않을 만큼 여유를 둔다.
 */
class ResponsivenessTest {

    private static final long MAX_AVERAGE_FRAME_NANOS = 4_000_000L;

    private final TestApplication app = new TestApplication().withPieces(TetrominoType.T);
    private final ScreenRouter router = app.router();

    private GameScreen startGame() {
        router.startGame();
        return (GameScreen) router.current();
    }

    private static ActivePiece piece(GameScreen game) {
        return game.session().snapshot().engine().activePiece();
    }

    @Test
    void aKeyPressIsAppliedInTheVeryNextFrame() {
        GameScreen game = startGame();
        int x = piece(game).origin().x();

        router.keyPressed("LEFT");
        router.update(FRAME);

        assertEquals(x - 1, piece(game).origin().x());
    }

    @Test
    void rapidRepeatedKeyPressesWithinOneFrameAreNeverDropped() {
        GameScreen game = startGame();
        int x = piece(game).origin().x();

        for (int i = 0; i < 3; i++) {
            router.keyPressed("LEFT");
            router.keyReleased("LEFT");
        }
        router.update(FRAME);

        assertEquals(x - 3, piece(game).origin().x());
    }

    @Test
    void holdingAKeyKeepsMovingWithoutFurtherPresses() {
        GameScreen game = startGame();
        int y = piece(game).origin().y();

        router.keyPressed("DOWN");
        for (int frame = 0; frame < 32; frame++) {
            router.update(FRAME); // 약 0.5초
        }

        // 처음 1칸 + 170ms 뒤부터 50ms마다 반복 → 0.5초 동안 최소 7칸
        assertTrue(piece(game).origin().y() - y >= 7, "moved " + (piece(game).origin().y() - y));
    }

    @Test
    void updatingAndDrawingAFrameTakesFarLessThanTheFrameBudget() {
        startGame();
        for (int frame = 0; frame < 300; frame++) {
            router.update(FRAME);
            router.render();
        }

        int frames = 2_000;
        long start = System.nanoTime();
        for (int frame = 0; frame < frames; frame++) {
            router.update(FRAME);
            router.render();
        }
        long average = (System.nanoTime() - start) / frames;

        assertTrue(average < MAX_AVERAGE_FRAME_NANOS, "average frame took " + average + "ns");
    }
}
