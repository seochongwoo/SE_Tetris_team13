package team.tetris.ui.screen;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import team.tetris.application.GameCommand;
import team.tetris.application.battle.BattleEnd;
import team.tetris.application.battle.BattlePlayer;
import team.tetris.application.battle.BattleResult;
import team.tetris.application.battle.BattleSession;
import team.tetris.application.battle.BattleSnapshot;
import team.tetris.application.battle.BattleStatus;
import team.tetris.application.battle.StartedBattle;
import team.tetris.application.model.Settings;
import team.tetris.ui.ScreenRouter;
import team.tetris.ui.input.BattleKeys;
import team.tetris.ui.input.InputMapper;
import team.tetris.ui.input.KeyNames;
import team.tetris.ui.input.RepeatController;
import team.tetris.ui.render.BattleRenderer;
import team.tetris.ui.render.TextFrame;
import team.tetris.ui.render.palette.ColorPalette;

/**
 * 로컬 대전 화면 (3차 카드 19). 두 플레이어의 키를 각자의 조작으로 바꿔 큐에 넣고, 매 프레임 대전 시간을
 * 먼저 흘린 뒤 큐 순서대로 전달한다 ({@link GameScreen}과 같은 시간·스레드 계약).
 *
 * <ul>
 *   <li>키 반복은 키마다 따로 만들므로 두 명이 동시에 누르고 있어도 서로의 반복이 끊기지 않는다 (H3-06).</li>
 *   <li>한 플레이어가 줄 삭제 연출 중이면 그 플레이어의 조작만 보관했다가 연출이 끝나면 전달한다.
 *       상대 플레이어는 그대로 조작한다.</li>
 *   <li>일시정지 키나 게임 메뉴 키를 누르면 두 보드가 함께 멈추고 메뉴가 열린다 (안건 ⑱). 일시정지 키는
 *       두 사람이 같이 쓰므로 메뉴에서 기권할 플레이어를 고르게 하고, 승패 없이 그만두는 항목도 둔다.</li>
 *   <li>결과가 나오면 승자를 보여 준 뒤 다시 대전 / 메뉴로 / 프로그램 종료를 고른다. 기록은 남기지 않는다
 *       (R3-12, R3-13, H3-14).</li>
 * </ul>
 */
public final class BattleScreen implements Screen {

    /** 결과가 나온 직후 조작 키가 그대로 결과 메뉴를 고르지 않도록 잠시 입력을 받지 않는다. */
    static final long RESULT_INPUT_DELAY_NANOS = 800_000_000L;
    static final List<String> PAUSE_ITEMS = List.of("재개", "1P 기권", "2P 기권", "그만두고 메뉴로", "프로그램 종료");
    static final List<String> RESULT_ITEMS = List.of("다시 대전", "메뉴로", "프로그램 종료");
    private static final int RESUME_ITEM = 0;
    private static final int FORFEIT_ONE_ITEM = 1;
    private static final int FORFEIT_TWO_ITEM = 2;
    private static final int ABORT_ITEM = 3;
    private static final int REMATCH_ITEM = 0;
    private static final int MENU_ITEM = 1;

    /** 큐에 쌓인 한 번의 조작. 공통 명령(정지·재개)은 player가 누구든 양쪽에 적용된다. */
    private record Input(BattlePlayer player, GameCommand command) {
    }

    private final ScreenRouter router;
    private final BattleSession session;
    private final BattleKeys keys;
    private final RepeatController repeats;
    private final BattleRenderer renderer;
    private final ColorPalette palette;
    private final String settingsWarning;
    private final Deque<Input> pending = new ArrayDeque<>();
    private final Map<BattlePlayer, Deque<GameCommand>> animationInput = new EnumMap<>(BattlePlayer.class);
    private long clockNanos;
    private long resultNanos;
    private int cursor;
    private boolean resultSeen;
    private boolean left;

    public BattleScreen(ScreenRouter router, StartedBattle started) {
        this(router, started, new RepeatController());
    }

    BattleScreen(ScreenRouter router, StartedBattle started, RepeatController repeats) {
        this.router = Objects.requireNonNull(router, "router");
        this.session = started.session();
        Settings settings = started.settings().value();
        this.keys = BattleKeys.defaults(settings);
        this.repeats = repeats;
        this.palette = ColorPalette.of(settings.colorBlindMode());
        this.renderer = new BattleRenderer(palette, keys);
        this.settingsWarning = started.settings().error().isPresent()
                ? "설정을 읽지 못해 기본값으로 진행합니다" : null;
        for (BattlePlayer player : BattlePlayer.values()) {
            animationInput.put(player, new ArrayDeque<>());
        }
    }

    /** 이 화면이 진행 중인 대전 (테스트용). */
    public BattleSession session() {
        return session;
    }

    /** 일시정지 메뉴나 결과 메뉴에서 고른 항목. */
    public int cursor() {
        return cursor;
    }

    public BattleKeys keys() {
        return keys;
    }

    // ---- 입력 ----

    @Override
    public void onKeyPressed(String key) {
        if (left) {
            return;
        }
        if (session.result().isPresent()) {
            pressInResult(key);
            return;
        }
        switch (session.snapshot().status()) {
            case RUNNING -> pressDuringPlay(key);
            case PAUSED -> pressInPauseMenu(key);
            case FINISHED -> {
            }
        }
    }

    private void pressDuringPlay(String key) {
        if (key.equals(keys.pauseKey()) || key.equals(keys.menuKey())) {
            if (repeats.press(key, clockNanos, false)) {
                pending.add(new Input(BattlePlayer.ONE, GameCommand.PAUSE));
            }
            return;
        }
        Optional<BattleKeys.Binding> binding = keys.bindingFor(key);
        if (binding.isEmpty()) {
            return;
        }
        GameCommand command = binding.get().command();
        if (!repeats.press(key, clockNanos, InputMapper.isRepeatable(command))) {
            return; // 떼지 않은 키의 OS 자동 반복 (반복은 직접 만든다)
        }
        pending.add(new Input(binding.get().player(), command));
    }

    private void pressInPauseMenu(String key) {
        if (!repeats.press(key, clockNanos, false)) {
            return;
        }
        if (isSelectKey(key)) {
            choosePauseItem();
        } else if (key.equals(keys.resumeKey()) || key.equals(keys.menuKey())) {
            pending.add(new Input(BattlePlayer.ONE, GameCommand.RESUME));
        } else {
            moveCursor(key, PAUSE_ITEMS.size());
        }
    }

    private void choosePauseItem() {
        switch (cursor) {
            case RESUME_ITEM -> pending.add(new Input(BattlePlayer.ONE, GameCommand.RESUME));
            case FORFEIT_ONE_ITEM -> pending.add(new Input(BattlePlayer.ONE, GameCommand.QUIT_GAME));
            case FORFEIT_TWO_ITEM -> pending.add(new Input(BattlePlayer.TWO, GameCommand.QUIT_GAME));
            case ABORT_ITEM -> leave(router::showMenu);
            default -> leave(router::exit);
        }
    }

    private void pressInResult(String key) {
        // 대기 중에도 누른 키를 기록해, 대기가 끝난 뒤 OS 자동 반복을 새 입력으로 받지 않는다.
        if (!repeats.press(key, clockNanos, false) || resultNanos < RESULT_INPUT_DELAY_NANOS) {
            return;
        }
        if (isSelectKey(key)) {
            switch (cursor) {
                case REMATCH_ITEM -> leave(() -> router.startBattle(session.snapshot().mode()));
                case MENU_ITEM -> leave(router::showMenu);
                default -> leave(router::exit);
            }
        } else {
            moveCursor(key, RESULT_ITEMS.size());
        }
    }

    /** 메뉴 선택: Enter 또는 Space. 두 사람 누구나 고를 수 있다. */
    private static boolean isSelectKey(String key) {
        return KeyNames.ENTER.equals(key) || "SPACE".equals(key);
    }

    /** 메뉴 이동: ↑·↓(2P 쪽) 또는 W·S(1P 쪽). */
    private void moveCursor(String key, int size) {
        if (KeyNames.UP.equals(key) || "W".equals(key)) {
            cursor = Math.floorMod(cursor - 1, size);
        } else if (KeyNames.DOWN.equals(key) || "S".equals(key)) {
            cursor = Math.floorMod(cursor + 1, size);
        }
    }

    private void leave(Runnable next) {
        if (session.result().isEmpty()) {
            session.abort();
        }
        left = true;
        clearPendingInput();
        next.run();
    }

    @Override
    public void onKeyReleased(String key) {
        repeats.release(key);
    }

    @Override
    public void onFocusLost() {
        clearPendingInput();
        if (!left && session.snapshot().status() == BattleStatus.RUNNING) {
            pending.add(new Input(BattlePlayer.ONE, GameCommand.PAUSE));
        }
    }

    private void clearPendingInput() {
        pending.clear();
        animationInput.values().forEach(Deque::clear);
        repeats.releaseAll();
    }

    // ---- 시간 ----

    @Override
    public void update(long elapsedNanos) {
        if (left) {
            return;
        }
        clockNanos += elapsedNanos;
        if (session.result().isPresent()) {
            showResult(elapsedNanos);
            return;
        }
        session.update(elapsedNanos);
        BattleSnapshot now = session.snapshot();
        if (now.status() == BattleStatus.RUNNING) {
            for (String key : repeats.due(clockNanos)) {
                keys.bindingFor(key).filter(binding -> InputMapper.isRepeatable(binding.command()))
                        .ifPresent(binding -> pending.add(new Input(binding.player(), binding.command())));
            }
            for (BattlePlayer player : BattlePlayer.values()) {
                if (now.of(player).lineClearAnimation() == null) {
                    // 연출 중에 보관한 조작을 이번 프레임의 새 입력보다 먼저 전달한다.
                    Deque<GameCommand> held = animationInput.get(player);
                    while (!held.isEmpty()) pending.addFirst(new Input(player, held.removeLast()));
                }
            }
        }
        while (!pending.isEmpty() && session.result().isEmpty()) {
            BattleStatus before = session.snapshot().status();
            Input input = pending.poll();
            if (before == BattleStatus.RUNNING && isPlay(input.command())
                    && session.snapshot().of(input.player()).lineClearAnimation() != null) {
                animationInput.get(input.player()).add(input.command());
                continue;
            }
            session.handle(input.player(), input.command());
            BattleStatus after = session.snapshot().status();
            if (before != after && (before == BattleStatus.PAUSED || after == BattleStatus.PAUSED)) {
                // 일시정지 메뉴와 게임 사이에 이전 입력이 넘어가지 않도록 새로 눌러야 한다.
                clearPendingInput();
                router.ignoreHeldKeys();
                cursor = RESUME_ITEM;
            }
        }
        if (session.result().isPresent()) {
            showResult(0);
        }
    }

    private static boolean isPlay(GameCommand command) {
        return command != GameCommand.PAUSE && command != GameCommand.RESUME && command != GameCommand.QUIT_GAME;
    }

    /** 결과가 처음 나온 프레임에 입력을 비우고 결과 메뉴를 준비한다. */
    private void showResult(long elapsedNanos) {
        if (!resultSeen) {
            resultSeen = true;
            clearPendingInput();
            router.ignoreHeldKeys();
            cursor = REMATCH_ITEM;
            resultNanos = 0;
            return;
        }
        resultNanos = Math.min(RESULT_INPUT_DELAY_NANOS, resultNanos + elapsedNanos);
    }

    // ---- 그리기 ----

    @Override
    public TextFrame render() {
        BattleSnapshot snapshot = session.snapshot();
        TextFrame frame = renderer.render(snapshot);
        if (settingsWarning != null) {
            frame.put(1, TextFrame.ROWS - 1, settingsWarning, palette.warningStyle());
        }
        Optional<BattleResult> result = session.result();
        if (result.isPresent()) {
            frame.shade();
            drawOutcomeTags(frame, result.get());
            drawMenu(frame, resultTitle(result.get()), scoreLine(result.get()), RESULT_ITEMS);
        } else if (snapshot.status() == BattleStatus.PAUSED) {
            frame.shade();
            drawMenu(frame, "일시정지", null, PAUSE_ITEMS);
        }
        return frame;
    }

    static String resultTitle(BattleResult result) {
        if (result.end() == BattleEnd.ABORTED) {
            return "대전 중단";
        }
        if (result.draw()) {
            return "무승부";
        }
        String winner = result.winner() == BattlePlayer.ONE ? "1P" : "2P";
        return result.end() == BattleEnd.FORFEIT ? winner + " 승리 (기권승)" : winner + " 승리!";
    }

    private static String scoreLine(BattleResult result) {
        return "1P " + result.scoreOne() + "  :  " + result.scoreTwo() + " 2P";
    }

    /** 각 보드 위쪽에 승리·패배를 표시한다. */
    private void drawOutcomeTags(TextFrame frame, BattleResult result) {
        for (BattlePlayer player : BattlePlayer.values()) {
            String tag;
            if (result.winner() == player) {
                tag = "WIN";
            } else if (result.winner() == player.opponent() || result.draw()) {
                tag = result.draw() ? "DRAW" : "LOSE";
            } else {
                continue;
            }
            // 블록 타일(두 칸) 경계에 맞춰야 반쪽 남은 타일이 글자로 보이지 않는다: 보드 칸 2~7을 덮는다.
            int left = BattleRenderer.leftOf(player) + 1 + 2 * 2;
            frame.box(left, 3, 12, 3, palette.borderStyle(), palette.base());
            frame.putCentered(left, left + 12, 4, tag,
                    result.winner() == player ? palette.accentStyle() : palette.warningStyle());
        }
    }

    /** 화면 가운데 메뉴 상자. 메뉴 조작 키 안내를 맨 아래에 둔다. */
    private void drawMenu(TextFrame frame, String title, String subtitle, List<String> items) {
        int width = 32;
        int extra = subtitle == null ? 0 : 1;
        int height = items.size() + 6 + extra;
        int col = (BattleRenderer.COLUMNS - width) / 2;
        int top = (TextFrame.ROWS - height) / 2;
        frame.box(col, top, width, height, palette.borderStyle(), palette.base());
        frame.putCentered(col, col + width, top + 1, title, palette.accentStyle());
        if (subtitle != null) {
            frame.putCentered(col, col + width, top + 2, subtitle, palette.base());
        }
        for (int i = 0; i < items.size(); i++) {
            int row = top + 3 + extra + i;
            if (i == cursor) {
                frame.fill(col + 1, row, width - 2, 1, palette.highlightStyle());
                frame.putCentered(col, col + width, row, "► " + items.get(i), palette.highlightStyle());
            } else {
                frame.putCentered(col, col + width, row, items.get(i), palette.base());
            }
        }
        frame.putCentered(col, col + width, top + height - 2, "↑↓ / W S 이동 · Enter 선택", palette.dimStyle());
    }
}
