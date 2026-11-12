package team.tetris.ui.screen;

import team.tetris.application.GameCommand;
import team.tetris.application.GameStatus;
import team.tetris.application.network.*;
import team.tetris.ui.ScreenRouter;
import team.tetris.ui.input.*;
import team.tetris.ui.render.*;

/** 로비 연결을 유지하며 경기를 진행하고 결과를 2초간 표시한다. */
public final class P2pMatchScreen implements Screen {
    private final ScreenRouter router;
    private final P2pLobby lobby;
    private final P2pMatch match;
    private final InputMapper keys;
    private final RepeatController repeats = new RepeatController();
    private long now;
    private long resultElapsed;
    private boolean handedOff;

    public P2pMatchScreen(ScreenRouter router, P2pLobby lobby, P2pMatch match) {
        this.router = router;
        this.lobby = lobby;
        this.match = match;
        keys = new InputMapper(router.settings());
    }
    public P2pMatch match() { return match; }
    private GameStatus status() { return match.paused() ? GameStatus.PAUSED : GameStatus.RUNNING; }
    @Override public void onKeyPressed(String key) {
        if (match.failure().isPresent()) {
            if (key.equals("ENTER") || key.equals("ESCAPE")) router.showP2pLobby();
            return;
        }
        keys.commandFor(key, status()).ifPresent(command -> {
            if (repeats.press(key, now, InputMapper.isRepeatable(command))) match.handle(command);
        });
    }
    @Override public void onKeyReleased(String key) { repeats.release(key); }
    @Override public void onFocusLost() { repeats.releaseAll(); match.handle(GameCommand.PAUSE); }
    @Override public void update(long elapsedNanos) {
        now += elapsedNanos;
        boolean paused = match.paused();
        boolean finished = match.result().isPresent();
        match.update(elapsedNanos);
        if (match.paused() != paused || match.result().isPresent()) {
            repeats.releaseAll();
            router.ignoreHeldKeys();
        }
        for (String key : repeats.due(now)) keys.commandFor(key, status()).ifPresent(match::handle);
        if (finished && match.failure().isEmpty()) {
            resultElapsed = Math.min(2_000_000_000L, resultElapsed + Math.min(elapsedNanos, 2_000_000_000L));
            if (resultElapsed == 2_000_000_000L) {
                handedOff = true;
                router.returnToP2pLobby(lobby);
            }
        }
    }
    @Override public TextFrame render() {
        TextFrame frame = new P2pRenderer(router.palette()).render(match);
        long input = match.inputId(), remoteInput = match.remoteInputId();
        frame.afterPaint(() -> match.painted(input, remoteInput));
        if (match.failure().isPresent()) {
            frame.fill(0, 24, frame.columns(), 2, router.palette().base());
            frame.putCentered(24, "연결 오류: " + match.failure().orElseThrow(), router.palette().warningStyle());
            frame.putCentered(25, "Enter: P2P 첫 화면", router.palette().base());
        }
        frame.putCentered(26, keys.keyFor(GameCommand.MOVE_LEFT) + "/" + keys.keyFor(GameCommand.MOVE_RIGHT)
                + " 이동 · " + keys.keyFor(GameCommand.ROTATE_CW) + " 회전 · " + keys.keyFor(GameCommand.HARD_DROP) + " 떨어뜨리기", router.palette().base());
        frame.putCentered(27, keys.keyFor(GameCommand.PAUSE) + " 정지 · " + keys.keyFor(GameCommand.RESUME)
                + " 재개 · " + keys.keyFor(GameCommand.QUIT_GAME) + " 기권", router.palette().base());
        return frame;
    }
    @Override public void onLeave() { match.finishMeasurement(); if (!handedOff) lobby.close(); }
}
