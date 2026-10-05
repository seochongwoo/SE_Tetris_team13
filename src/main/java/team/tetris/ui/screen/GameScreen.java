package team.tetris.ui.screen;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Optional;
import team.tetris.application.GameCommand;
import team.tetris.application.GameResult;
import team.tetris.application.GameSession;
import team.tetris.application.GameSnapshot;
import team.tetris.application.GameStatus;
import team.tetris.application.StartedGame;
import team.tetris.application.model.Settings;
import team.tetris.ui.ScreenRouter;
import team.tetris.ui.input.InputMapper;
import team.tetris.ui.input.KeyNames;
import team.tetris.ui.input.RepeatController;
import team.tetris.ui.render.Renderer;
import team.tetris.ui.render.TextFrame;
import team.tetris.ui.render.TextRenderer;
import team.tetris.ui.render.palette.ColorPalette;

/**
 * 게임 화면. 키 입력을 명령 큐에 넣었다가 매 프레임 {@link #update}에서 세션 시간을 먼저 흘린 뒤
 * 큐에 쌓인 순서대로 세션에 전달한다 (개발 가이드의 시간·스레드 계약).
 *
 * <p>일시정지 중에는 "재개 / 메뉴로 / 프로그램 종료" 메뉴를 띄운다. 일시정지 키뿐 아니라 게임 메뉴
 * 키(기본 Esc)도 같은 메뉴를 연다. 게임이 끝나면 잠시 GAME OVER를 보여준 뒤 종료 흐름으로 넘어간다.
 */
public final class GameScreen implements Screen {

    static final long GAME_OVER_DELAY_NANOS = 1_200_000_000L;
    private static final String[] PAUSE_ITEMS = {"재개", "메뉴로", "프로그램 종료"};
    private static final int RESUME_ITEM = 0;
    private static final int QUIT_ITEM = 1;

    private final ScreenRouter router;
    private final GameSession session;
    private final InputMapper keys;
    private final RepeatController repeats;
    private final Renderer renderer;
    private final ColorPalette palette;
    private final String settingsWarning;
    private final Deque<GameCommand> pending = new ArrayDeque<>();
    private long clockNanos;
    private long gameOverNanos;
    private int pauseCursor;
    private boolean finished;

    public GameScreen(ScreenRouter router, StartedGame started) {
        this(router, started, new RepeatController());
    }

    GameScreen(ScreenRouter router, StartedGame started, RepeatController repeats) {
        this.router = router;
        this.session = started.session();
        Settings settings = started.settings().value();
        this.keys = new InputMapper(settings);
        this.repeats = repeats;
        this.palette = ColorPalette.of(settings.colorBlindMode());
        this.renderer = new TextRenderer(palette, keys);
        this.settingsWarning = started.settings().error().isPresent()
                ? "설정을 읽지 못해 기본값으로 진행합니다" : null;
    }

    /** 이 화면이 진행 중인 세션 (테스트와 성능 측정용). */
    public GameSession session() {
        return session;
    }

    public int pauseCursor() {
        return pauseCursor;
    }

    @Override
    public void onKeyPressed(String key) {
        if (finished) {
            return;
        }
        switch (session.snapshot().status()) {
            case RUNNING -> pressDuringPlay(key);
            case PAUSED -> pressInPauseMenu(key);
            case GAME_OVER -> {
                if (KeyNames.ENTER.equals(key)) {
                    gameOverNanos = GAME_OVER_DELAY_NANOS;
                }
            }
            case ABORTED -> {
            }
        }
    }

    private void pressDuringPlay(String key) {
        Optional<GameCommand> mapped = keys.commandFor(key, GameStatus.RUNNING);
        if (mapped.isEmpty()) {
            return;
        }
        GameCommand command = mapped.get();
        if (!repeats.press(key, clockNanos, InputMapper.isRepeatable(command))) {
            return; // 떼지 않은 키가 다시 들어온 것 = OS 자동 반복이므로 무시 (반복은 직접 만든다)
        }
        pending.add(command == GameCommand.QUIT_GAME ? GameCommand.PAUSE : command);
    }

    private void pressInPauseMenu(String key) {
        switch (key) {
            case KeyNames.UP -> pauseCursor = Math.floorMod(pauseCursor - 1, PAUSE_ITEMS.length);
            case KeyNames.DOWN -> pauseCursor = Math.floorMod(pauseCursor + 1, PAUSE_ITEMS.length);
            case KeyNames.ENTER -> choosePauseItem();
            // 재개 키(기본 P)나 게임 메뉴 키(기본 Esc)는 메뉴를 닫고 이어서 한다.
            default -> keys.commandFor(key, GameStatus.PAUSED).ifPresent(command -> pending.add(GameCommand.RESUME));
        }
    }

    private void choosePauseItem() {
        switch (pauseCursor) {
            case RESUME_ITEM -> pending.add(GameCommand.RESUME);
            case QUIT_ITEM -> pending.add(GameCommand.QUIT_GAME);
            default -> router.exit();
        }
    }

    @Override
    public void onKeyReleased(String key) {
        repeats.release(key);
    }

    @Override
    public void onFocusLost() {
        repeats.releaseAll();
        if (!finished && session.snapshot().status() == GameStatus.RUNNING) {
            pending.add(GameCommand.PAUSE);
        }
    }

    @Override
    public void update(long elapsedNanos) {
        if (finished) {
            return;
        }
        clockNanos += elapsedNanos;
        session.update(elapsedNanos);
        if (session.snapshot().status() == GameStatus.RUNNING) {
            for (String key : repeats.due(clockNanos)) {
                keys.commandFor(key, GameStatus.RUNNING).filter(InputMapper::isRepeatable).ifPresent(pending::add);
            }
        }
        while (!pending.isEmpty()) {
            session.handle(pending.poll());
        }
        if (session.snapshot().status() != GameStatus.PAUSED) {
            pauseCursor = RESUME_ITEM;
        }

        Optional<GameResult> result = session.result();
        if (result.isEmpty()) {
            return;
        }
        if (result.get().reason() == GameStatus.GAME_OVER && gameOverNanos < GAME_OVER_DELAY_NANOS) {
            gameOverNanos += elapsedNanos;
            if (gameOverNanos < GAME_OVER_DELAY_NANOS) {
                return;
            }
        }
        finished = true;
        repeats.releaseAll();
        router.finishGame(result.get());
    }

    @Override
    public TextFrame render() {
        GameSnapshot snapshot = session.snapshot();
        TextFrame frame = renderer.render(snapshot);
        if (settingsWarning != null) {
            frame.put(1, TextFrame.ROWS - 1, settingsWarning, palette.warningStyle());
        }
        if (snapshot.status() == GameStatus.PAUSED) {
            drawPauseMenu(frame);
        } else if (snapshot.status() == GameStatus.GAME_OVER) {
            drawGameOver(frame, snapshot.score());
        }
        return frame;
    }

    private void drawPauseMenu(TextFrame frame) {
        // 보드 테두리와 같은 폭으로 덮어서 이중 테두리가 생기지 않게 한다.
        int left = TextRenderer.BOARD_LEFT;
        int width = 22;
        int top = 7;
        frame.box(left, top, width, 10, palette.borderStyle(), palette.base());
        frame.putCentered(left, left + width, top + 1, "일시정지", palette.accentStyle());
        for (int i = 0; i < PAUSE_ITEMS.length; i++) {
            int row = top + 3 + i;
            if (i == pauseCursor) {
                frame.fill(left + 1, row, width - 2, 1, palette.highlightStyle());
                frame.putCentered(left, left + width, row, "► " + PAUSE_ITEMS[i], palette.highlightStyle());
            } else {
                frame.putCentered(left, left + width, row, PAUSE_ITEMS[i], palette.base());
            }
        }
        frame.putCentered(left, left + width, top + 7, "↑↓ 이동", palette.dimStyle());
        frame.putCentered(left, left + width, top + 8, "Enter 선택", palette.dimStyle());
    }

    private void drawGameOver(TextFrame frame, long score) {
        int left = TextRenderer.BOARD_LEFT + 2;
        int width = 18;
        int top = 9;
        frame.box(left, top, width, 5, palette.borderStyle(), palette.base());
        frame.putCentered(left, left + width, top + 1, "GAME OVER", palette.warningStyle());
        frame.putCentered(left, left + width, top + 2, "점수 " + score, palette.base());
        frame.putCentered(left, left + width, top + 3, "Enter 계속", palette.dimStyle());
    }
}
