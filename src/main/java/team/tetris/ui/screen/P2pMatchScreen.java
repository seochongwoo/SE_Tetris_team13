package team.tetris.ui.screen;

import team.tetris.application.GameCommand;
import team.tetris.application.GameStatus;
import team.tetris.application.battle.BattlePlayer;
import java.util.List;
import team.tetris.application.network.*;
import team.tetris.ui.ScreenRouter;
import team.tetris.ui.input.*;
import team.tetris.ui.render.*;

/**
 * 로비 연결을 유지하며 경기를 진행하고 결과를 2초간 표시한다. 일시정지·결과 상자와 WIN/LOSE 표시는 로컬
 * 대전과 같은 모양({@link BattleOverlay})을 쓴다.
 */
public final class P2pMatchScreen implements Screen {
    private final ScreenRouter router;
    private final P2pLobby lobby;
    private final P2pMatch match;
    private final InputMapper keys;
    private final RepeatController repeats = new RepeatController();
    private long now;
    private long resultElapsed;
    private boolean handedOff;
    private boolean failureShown;
    private long failureElapsed;
    /** 일시정지 메뉴에서 고른 항목. 정지가 시작될 때마다 맨 위로 돌아간다. */
    private int pauseCursor;
    private boolean pausedBefore;

    static final String RESUME_ITEM = "재개";
    static final String FORFEIT_ITEM = "기권";

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
        if (match.paused() && match.result().isEmpty() && pauseMenuKey(key)) {
            return;
        }
        keys.commandFor(key, status()).ifPresent(command -> {
            if (repeats.press(key, now, InputMapper.isRepeatable(command))) match.handle(
                    command == GameCommand.QUIT_GAME && !match.paused() ? GameCommand.PAUSE : command);
        });
    }
    /** 일시정지 메뉴를 ↑↓·Enter로 고른다. 메뉴 키였으면 true. P(재개)와 Esc(기권) 바로 가기는 그대로 쓴다. */
    private boolean pauseMenuKey(String key) {
        List<String> items = pauseItems();
        switch (key) {
            case KeyNames.UP, KeyNames.DOWN -> {
                pauseCursor = Math.floorMod(pauseCursor + (key.equals(KeyNames.UP) ? -1 : 1), items.size());
                return true;
            }
            case KeyNames.ENTER -> {
                match.handle(items.get(pauseCursor).equals(RESUME_ITEM) ? GameCommand.RESUME : GameCommand.QUIT_GAME);
                return true;
            }
            default -> {
                return false;
            }
        }
    }

    /** 정지를 건 쪽만 재개할 수 있다. 상대가 건 정지에서는 기권만 고를 수 있다. */
    List<String> pauseItems() {
        return match.pauseOwner() == match.role() ? List.of(RESUME_ITEM, FORFEIT_ITEM) : List.of(FORFEIT_ITEM);
    }

    int pauseCursor() { return pauseCursor; }

    @Override public void onKeyReleased(String key) { repeats.release(key); }
    @Override public void onFocusLost() { repeats.releaseAll(); }
    @Override public void update(long elapsedNanos) {
        now += elapsedNanos;
        boolean paused = match.paused();
        boolean finished = match.result().isPresent();
        match.update(elapsedNanos);
        if (match.paused() && !pausedBefore) pauseCursor = 0;
        pausedBefore = match.paused();
        if (match.failure().isPresent()) {
            repeats.releaseAll();
            if (failureShown) failureElapsed += Math.min(elapsedNanos, 2_000_000_000L);
            else { failureShown = true; failureElapsed = 0; router.ignoreHeldKeys(); }
            if (failureElapsed >= 2_000_000_000L) router.showP2pLobby();
            return;
        }
        if (match.paused() != paused || match.result().isPresent()) {
            repeats.releaseAll();
            router.ignoreHeldKeys();
        }
        for (String key : repeats.due(now)) keys.commandFor(key, status()).ifPresent(match::handle);
        if (finished && match.failure().isEmpty()) {
            resultElapsed = Math.min(2_000_000_000L, resultElapsed + Math.min(elapsedNanos, 2_000_000_000L));
            if (resultElapsed == 2_000_000_000L) {
                if (match.peerDisconnected()) {
                    router.showP2pLobby();
                    return;
                }
                handedOff = true;
                router.returnToP2pLobby(lobby);
            }
        }
    }
    @Override public TextFrame render() {
        TextFrame frame = new P2pRenderer(router.palette()).render(match);
        long input = match.inputId(), remoteInput = match.remoteInputId();
        frame.afterPaint(() -> match.painted(input, remoteInput));
        var palette = router.palette();
        BattleRenderer.drawCommonKeys(frame, palette, displayKey(GameCommand.PAUSE), displayKey(GameCommand.QUIT_GAME));
        BattleRenderer.drawControls(frame, palette, 0, BattleRenderer.leftOf(BattlePlayer.ONE), BattleRenderer.controls(
                displayKey(GameCommand.MOVE_LEFT), displayKey(GameCommand.MOVE_RIGHT), displayKey(GameCommand.ROTATE_CW),
                displayKey(GameCommand.SOFT_DROP), displayKey(GameCommand.HARD_DROP)),
                BattleRenderer.COLUMNS - 1 - BattleRenderer.leftOf(BattlePlayer.ONE));
        if (match.failure().isPresent()) {
            frame.shade();
            BattleOverlay.dialog(frame, palette, "연결 오류", match.failure().orElseThrow(), List.of(), 0,
                    "잠시 후 P2P 첫 화면 · Enter 바로 복귀");
        } else if (match.result().isPresent()) {
            MatchMessage.Outcome outcome = match.result().orElseThrow().outcome();
            boolean draw = outcome == MatchMessage.Outcome.DRAW;
            boolean won = !draw && (outcome == MatchMessage.Outcome.SERVER_WINS) == (match.role() == MatchMessage.Role.SERVER);
            String title = draw ? "무승부" : won ? "승리" : "패배";
            frame.shade();
            BattleOverlay.outcomeTag(frame, palette, BattleRenderer.leftOf(BattlePlayer.ONE), draw ? "DRAW" : won ? "WIN" : "LOSE", won);
            BattleOverlay.outcomeTag(frame, palette, BattleRenderer.leftOf(BattlePlayer.TWO), draw ? "DRAW" : won ? "LOSE" : "WIN",
                    !draw && !won);
            if (match.peerDisconnected()) {
                BattleOverlay.dialog(frame, palette, title + " · 연결 끊김", match.disconnectReason(), List.of(), 0,
                        "잠시 후 서버/클라이언트 선택 화면으로 돌아갑니다");
            } else {
                String scores = "나 " + match.local().score() + match.remote().map(r -> "  :  " + r.score() + " 상대").orElse("");
                BattleOverlay.dialog(frame, palette, title, scores, List.of(), 0, "잠시 후 대기실로 돌아갑니다");
            }
        } else if (match.paused()) {
            frame.shade();
            boolean mine = match.pauseOwner() == match.role();
            BattleOverlay.dialog(frame, palette, "일시정지", mine ? null : "상대가 재개할 때까지 대기", pauseItems(),
                    pauseCursor, "↑↓ 이동 · Enter 선택" + (mine ? " · " + displayKey(GameCommand.RESUME) + " 재개" : ""));
        }
        return frame;
    }

    private String displayKey(GameCommand command) { return KeyNames.display(keys.keyFor(command)); }
    @Override public void onLeave() { match.finishMeasurement(); if (!handedOff) lobby.close(); }
}
