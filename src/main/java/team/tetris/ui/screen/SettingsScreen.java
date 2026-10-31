package team.tetris.ui.screen;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import team.tetris.application.GameCommand;
import team.tetris.application.model.Settings;
import team.tetris.application.model.Difficulty;
import team.tetris.application.model.Settings.ScreenSize;
import team.tetris.application.port.StorageException;
import team.tetris.ui.DisplayNames;
import team.tetris.ui.ScreenRouter;
import team.tetris.ui.input.KeyNames;
import team.tetris.ui.render.TextFrame;
import team.tetris.ui.render.TextStyle;
import team.tetris.ui.render.palette.ColorPalette;

/**
 * 설정 화면: 난이도, 화면 크기, 색맹 모드, 조작 키 설정, 스코어보드 초기화, 기본 설정 복원.
 * 바꾼 값은 그 자리에서 저장되고, 저장에 성공했을 때만 화면에 적용된다.
 *
 * <p>값이 있는 줄은 선택지를 한 줄에 모두 보여주고 현재 값을 [ ]로 표시한다. ←→는 그 선택지로 바로
 * 옮기고(끝에서 멈춤), Enter는 다음 값으로 넘긴다(끝에서 처음으로). 선택한 줄의 설명은 목록 아래에 보인다.
 *
 * <p>조작 키 8개는 "조작 키 설정" 안쪽 페이지에 둔다. 키를 바꿀 때는 해당 줄에서 Enter를 누른 뒤 새 키를
 * 누른다 (Backspace는 취소). 다른 조작과 겹치는 키는 설정 모델이 거부하므로 그 결과를 그대로 안내한다.
 */
public final class SettingsScreen implements Screen {

    private enum Kind { DIFFICULTY, SCREEN_SIZE, COLOR_BLIND, KEYS, CLEAR_SCORES, RESET, BACK }

    private record Row(Kind kind, String label, String help) {
    }

    private static final Map<GameCommand, String> COMMAND_LABELS = Map.of(
            GameCommand.MOVE_LEFT, "왼쪽 이동",
            GameCommand.MOVE_RIGHT, "오른쪽 이동",
            GameCommand.SOFT_DROP, "아래로 이동",
            GameCommand.HARD_DROP, "한 번에 떨어뜨리기",
            GameCommand.ROTATE_CW, "회전",
            GameCommand.PAUSE, "일시정지",
            GameCommand.RESUME, "재개",
            GameCommand.QUIT_GAME, "게임 메뉴");

    private static final List<Row> ROWS = List.of(
            new Row(Kind.DIFFICULTY, "난이도", null),
            new Row(Kind.SCREEN_SIZE, "화면 크기", "창과 글자 크기를 바꿉니다"),
            new Row(Kind.COLOR_BLIND, "색맹 모드", "적록·청황 색맹도 구분하기 쉬운 색을 씁니다|블록 무늬는 두 모드 모두 표시됩니다"),
            new Row(Kind.KEYS, "조작 키 설정", "게임 조작 키 8개를 바꿉니다"),
            new Row(Kind.CLEAR_SCORES, "스코어보드 초기화", "모든 모드·난이도의 기록을 지웁니다"),
            new Row(Kind.RESET, "기본 설정으로 되돌리기", "모든 설정을 처음 값으로 되돌립니다"),
            new Row(Kind.BACK, "돌아가기", "메인 메뉴로 돌아갑니다"));
    /** 조작 키 페이지의 줄: 조작 8개와 돌아가기. */
    private static final List<GameCommand> KEY_ROWS = List.of(GameCommand.values());

    /** 선택지 줄에서 값을 그릴 칸: 선택지마다 같은 폭을 차지해 위아래 줄의 선택지가 맞춰진다. */
    private static final int OPTIONS_COL = 17;
    private static final int OPTION_WIDTH = 8;
    private static final int LIST_TOP = 4;
    private static final int LABEL_COL = 4;
    /** 조작 키 설정 줄 다음(스코어보드 초기화 앞)에 구분선을 둔다. */
    private static final int SEPARATOR_AFTER = 3;
    private static final int HELP_ROW = 14;
    private static final int MESSAGE_ROW = 17;
    private static final int KEY_VALUE_COL = 25;

    private final ScreenRouter router;
    private int cursor;
    private boolean editingKeys;
    private int keyCursor;
    private GameCommand waitingFor;
    private Kind confirming;
    private String message;
    private boolean messageIsError;

    public SettingsScreen(ScreenRouter router) {
        this.router = router;
    }

    /** 지금 보이는 페이지에서 선택된 줄. */
    public int cursor() {
        return editingKeys ? keyCursor : cursor;
    }

    /** 조작 키 설정 페이지를 보고 있는가. */
    public boolean isEditingKeys() {
        return editingKeys;
    }

    public boolean isWaitingForKey() {
        return waitingFor != null;
    }

    public String message() {
        return message;
    }

    @Override
    public void onKeyPressed(String key) {
        if (waitingFor != null) {
            if (KeyNames.BACK_SPACE.equals(key)) {
                waitingFor = null;
                info("키 변경을 취소했습니다");
            } else {
                bind(waitingFor, key);
            }
            return;
        }
        if (confirming != null) {
            if (KeyNames.ENTER.equals(key)) {
                runConfirmed();
            } else {
                confirming = null;
                info("취소했습니다");
            }
            return;
        }
        if (editingKeys) {
            pressOnKeyPage(key);
            return;
        }
        switch (key) {
            case KeyNames.UP -> cursor = Math.floorMod(cursor - 1, ROWS.size());
            case KeyNames.DOWN -> cursor = Math.floorMod(cursor + 1, ROWS.size());
            case KeyNames.LEFT -> choose(-1, false);
            case KeyNames.RIGHT -> choose(1, false);
            case KeyNames.ENTER -> activate();
            case KeyNames.ESCAPE -> router.showMenu();
            default -> info("사용할 수 있는 키: ↑ ↓ ← → Enter Esc");
        }
    }

    private void pressOnKeyPage(String key) {
        int rows = KEY_ROWS.size() + 1;
        switch (key) {
            case KeyNames.UP -> keyCursor = Math.floorMod(keyCursor - 1, rows);
            case KeyNames.DOWN -> keyCursor = Math.floorMod(keyCursor + 1, rows);
            case KeyNames.ENTER -> {
                if (keyCursor == KEY_ROWS.size()) {
                    leaveKeyPage();
                } else {
                    // 이 단계를 연 Enter를 누르고 있으면 자동 반복이 곧바로 새 키(ENTER)로 처리되므로,
                    // 한 번 뗄 때까지 무시한다.
                    waitingFor = KEY_ROWS.get(keyCursor);
                    message = null;
                    router.ignoreHeldKeys();
                }
            }
            case KeyNames.ESCAPE -> leaveKeyPage();
            default -> info("사용할 수 있는 키: ↑ ↓ Enter Esc");
        }
    }

    private void leaveKeyPage() {
        editingKeys = false;
        message = null;
    }

    /**
     * 선택지 줄의 값을 바꾼다. ←→(wrap=false)는 끝에서 멈추고, Enter(wrap=true)는 끝에서 처음으로 돌아간다.
     * 값이 그대로면 저장하지 않는다.
     */
    private void choose(int direction, boolean wrap) {
        Kind kind = ROWS.get(cursor).kind();
        int count = optionCount(kind);
        if (count == 0) {
            return;
        }
        Settings current = router.settings();
        int index = selectedOption(kind, current);
        int next = wrap ? Math.floorMod(index + direction, count) : Math.max(0, Math.min(count - 1, index + direction));
        if (next == index) {
            return;
        }
        switch (kind) {
            case DIFFICULTY -> {
                Difficulty value = Difficulty.values()[next];
                save(new Settings(current.screenSize(), current.keyBindings(), current.colorBlindMode(), value),
                        "난이도 " + DisplayNames.of(value));
            }
            case SCREEN_SIZE -> {
                ScreenSize value = ScreenSize.values()[next];
                save(new Settings(value, current.keyBindings(), current.colorBlindMode(), current.difficulty()),
                        "화면 크기 " + DisplayNames.of(value));
            }
            case COLOR_BLIND -> {
                boolean value = next == 1;
                save(new Settings(current.screenSize(), current.keyBindings(), value, current.difficulty()),
                        "색맹 모드 " + DisplayNames.onOff(value));
            }
            default -> {
            }
        }
    }

    private void activate() {
        Kind kind = ROWS.get(cursor).kind();
        switch (kind) {
            case DIFFICULTY, SCREEN_SIZE, COLOR_BLIND -> choose(1, true);
            case KEYS -> {
                editingKeys = true;
                keyCursor = 0;
                message = null;
            }
            // 확인 단계를 연 Enter를 누르고 있으면 자동 반복이 곧바로 "예"로 처리되므로, 한 번 뗄 때까지 무시한다.
            case CLEAR_SCORES, RESET -> {
                confirming = kind;
                message = null;
                router.ignoreHeldKeys();
            }
            case BACK -> router.showMenu();
        }
    }

    private void bind(GameCommand command, String key) {
        Settings current = router.settings();
        Map<GameCommand, String> keys = new EnumMap<>(current.keyBindings());
        keys.put(command, key);
        waitingFor = null;
        Settings candidate;
        try {
            candidate = new Settings(current.screenSize(), keys, current.colorBlindMode(), current.difficulty());
        } catch (IllegalArgumentException conflict) {
            error(KeyNames.display(key) + ": 다른 조작과 겹치는 키입니다");
            return;
        }
        save(candidate, COMMAND_LABELS.get(command) + " → " + KeyNames.display(key));
    }

    private void runConfirmed() {
        Kind kind = confirming;
        confirming = null;
        try {
            if (kind == Kind.CLEAR_SCORES) {
                router.clearScores();
                info("스코어보드를 초기화했습니다");
            } else {
                router.resetSettings();
                info("기본 설정으로 되돌렸습니다");
            }
        } catch (StorageException failure) {
            error(kind == Kind.CLEAR_SCORES ? "스코어보드를 초기화하지 못했습니다" : "설정을 저장하지 못했습니다");
        }
    }

    private void save(Settings next, String description) {
        try {
            router.updateSettings(next);
            info("저장했습니다: " + description);
        } catch (StorageException failure) {
            error("설정을 저장하지 못했습니다");
        }
    }

    private void info(String text) {
        message = text;
        messageIsError = false;
    }

    private void error(String text) {
        message = text;
        messageIsError = true;
    }

    // ---- 선택지 ----

    private static int optionCount(Kind kind) {
        return switch (kind) {
            case DIFFICULTY -> Difficulty.values().length;
            case SCREEN_SIZE -> ScreenSize.values().length;
            case COLOR_BLIND -> 2;
            default -> 0;
        };
    }

    private static int selectedOption(Kind kind, Settings settings) {
        return switch (kind) {
            case DIFFICULTY -> settings.difficulty().ordinal();
            case SCREEN_SIZE -> settings.screenSize().ordinal();
            case COLOR_BLIND -> settings.colorBlindMode() ? 1 : 0;
            default -> -1;
        };
    }

    private static String optionLabel(Kind kind, int index) {
        return switch (kind) {
            case DIFFICULTY -> DisplayNames.of(Difficulty.values()[index]);
            case SCREEN_SIZE -> DisplayNames.of(ScreenSize.values()[index]);
            case COLOR_BLIND -> DisplayNames.onOff(index == 1);
            default -> "";
        };
    }

    /** 선택한 줄의 설명. 난이도는 지금 고른 값에 따라 달라진다. 줄바꿈은 |로 나눈다. */
    private static String helpOf(Row row, Settings settings) {
        if (row.kind() != Kind.DIFFICULTY) {
            return row.help();
        }
        return switch (settings.difficulty()) {
            case EASY -> "I 블록이 더 자주 나오고|레벨이 올라도 속도가 천천히 빨라집니다";
            case NORMAL -> "모든 블록이 같은 확률로 나오는|기본 난이도입니다";
            case HARD -> "I 블록이 덜 나오고|레벨이 오르면 속도가 빨리 빨라집니다";
        };
    }

    /** 화면에 나올 수 있는 모든 설명 줄 (테스트용: 화면 폭을 넘으면 끝이 잘린다). */
    static List<String> allHelpLines() {
        List<String> lines = new ArrayList<>();
        for (Difficulty difficulty : Difficulty.values()) {
            Settings settings = new Settings(Settings.defaults().screenSize(), Settings.defaults().keyBindings(),
                    false, difficulty);
            for (Row row : ROWS) {
                lines.addAll(List.of(helpOf(row, settings).split("\\|")));
            }
        }
        return lines;
    }

    // ---- 그리기 ----

    @Override
    public TextFrame render() {
        ColorPalette palette = router.palette();
        Settings settings = router.settings();
        TextFrame frame = TextFrame.blank(palette.base());
        frame.putCentered(1, editingKeys ? "조작 키 설정" : "설정", palette.accentStyle());
        rule(frame, 2, palette);
        if (editingKeys) {
            renderKeyPage(frame, palette, settings);
        } else {
            renderMainPage(frame, palette, settings);
        }

        if (waitingFor != null) {
            frame.putCentered(MESSAGE_ROW, "'" + COMMAND_LABELS.get(waitingFor) + "'에 쓸 키를 누르세요",
                    palette.warningStyle());
            frame.putCentered(MESSAGE_ROW + 1, "Backspace 취소", palette.dimStyle());
        } else if (confirming != null) {
            frame.putCentered(MESSAGE_ROW,
                    confirming == Kind.CLEAR_SCORES ? "스코어보드를 초기화할까요?" : "모든 설정을 되돌릴까요?",
                    palette.warningStyle());
            frame.putCentered(MESSAGE_ROW + 1, "Enter 예   다른 키 아니오", palette.dimStyle());
        } else if (message != null) {
            frame.putCentered(MESSAGE_ROW, message, messageIsError ? palette.warningStyle() : palette.accentStyle());
        }
        frame.putCentered(TextFrame.ROWS - 2, editingKeys
                ? "↑↓ 이동  Enter 키 바꾸기  Esc 뒤로"
                : "◄ ► 변경  ↑↓ 이동  Enter 선택  Esc 뒤로", palette.dimStyle());
        return frame;
    }

    private void renderMainPage(TextFrame frame, ColorPalette palette, Settings settings) {
        for (int i = 0; i < ROWS.size(); i++) {
            Row row = ROWS.get(i);
            int y = rowOf(i);
            boolean selected = i == cursor;
            TextStyle style = selected ? palette.highlightStyle() : palette.base();
            if (selected) {
                frame.fill(LABEL_COL - 2, y, TextFrame.COLUMNS - LABEL_COL, 1, style);
                frame.put(LABEL_COL - 2, y, "►", style);
            }
            frame.put(LABEL_COL, y, row.label(), style);
            int count = optionCount(row.kind());
            int chosen = selectedOption(row.kind(), settings);
            for (int option = 0; option < count; option++) {
                String label = optionLabel(row.kind(), option);
                boolean current = option == chosen;
                // 현재 값은 [ ]로 감싸고, 선택하지 않은 줄에서는 강조색으로도 표시한다.
                TextStyle optionStyle = selected ? style : current ? palette.accentStyle() : palette.dimStyle();
                frame.put(OPTIONS_COL + option * OPTION_WIDTH, y, current ? "[" + label + "]" : " " + label,
                        optionStyle);
            }
            if (row.kind() == Kind.KEYS) {
                frame.put(TextFrame.COLUMNS - 4, y, "►", style);
            }
        }
        rule(frame, rowOf(SEPARATOR_AFTER) + 1, palette);
        String help = helpOf(ROWS.get(cursor), settings);
        if (help != null && confirming == null && waitingFor == null) {
            String[] lines = help.split("\\|");
            for (int i = 0; i < lines.length; i++) {
                frame.putCentered(HELP_ROW + i, lines[i], palette.base());
            }
        }
    }

    private void renderKeyPage(TextFrame frame, ColorPalette palette, Settings settings) {
        for (int i = 0; i <= KEY_ROWS.size(); i++) {
            int y = LIST_TOP + i;
            boolean selected = i == keyCursor;
            TextStyle style = selected ? palette.highlightStyle() : palette.base();
            if (selected) {
                frame.fill(LABEL_COL - 2, y, TextFrame.COLUMNS - LABEL_COL, 1, style);
                frame.put(LABEL_COL - 2, y, "►", style);
            }
            if (i == KEY_ROWS.size()) {
                frame.put(LABEL_COL, y, "돌아가기", style);
                continue;
            }
            GameCommand command = KEY_ROWS.get(i);
            frame.put(LABEL_COL, y, COMMAND_LABELS.get(command), style);
            frame.put(KEY_VALUE_COL, y,
                    waitingFor == command ? "..." : KeyNames.display(settings.keyBindings().get(command)), style);
        }
    }

    /** 목록 i번째 줄의 화면 행. 구분선 아래 줄은 구분선 한 줄만큼 내린다. */
    private static int rowOf(int index) {
        return LIST_TOP + index + (index > SEPARATOR_AFTER ? 1 : 0);
    }

    private static void rule(TextFrame frame, int row, ColorPalette palette) {
        frame.put(LABEL_COL - 2, row, "─".repeat(TextFrame.COLUMNS - 2 * (LABEL_COL - 2)), palette.dimStyle());
    }
}
