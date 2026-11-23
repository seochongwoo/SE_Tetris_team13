package team.tetris.ui.screen;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import team.tetris.application.GameCommand;
import team.tetris.application.model.BattleKeyBindings;
import team.tetris.application.model.Settings;
import team.tetris.application.model.Difficulty;
import team.tetris.application.model.Settings.ScreenSize;
import team.tetris.application.port.StorageException;
import team.tetris.ui.DisplayNames;
import team.tetris.ui.ScreenRouter;
import team.tetris.ui.input.KeyNames;
import team.tetris.ui.render.MenuTheme;
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
 *
 * <p>로컬 대전의 1P·2P 조작 키는 "대전 조작 키" 안쪽 페이지에 둔다 (카드 18). ↑↓로 조작, ←→로 1P·2P를 고른다.
 * 일시정지·재개·게임 메뉴는 두 사람이 같이 쓰는 공통 키라 "조작 키 설정"의 키를 그대로 쓰며, 대전 조작 키와
 * 겹치는 키는 양쪽 페이지 모두에서 거부한다.
 */
public final class SettingsScreen implements Screen {

    private enum Kind { DIFFICULTY, SCREEN_SIZE, COLOR_BLIND, KEYS, BATTLE_KEYS, CLEAR_SCORES, RESET, BACK }

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
            new Row(Kind.BATTLE_KEYS, "대전 조작 키", "로컬 대전의 1P·2P 조작 키를 바꿉니다|일시정지·메뉴 키는 두 사람 공통입니다"),
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
    /** 대전 조작 키 줄 다음(스코어보드 초기화 앞)에 구분선을 둔다. */
    private static final int SEPARATOR_AFTER = 4;
    /** 대전 키 페이지: 조작 다섯 줄, 기본 키로 되돌리기, 돌아가기. */
    private static final int BATTLE_RESET_ROW = BattleKeyBindings.COMMANDS.size();
    private static final int BATTLE_BACK_ROW = BATTLE_RESET_ROW + 1;
    private static final int BATTLE_LABEL_COL = 4;
    private static final int[] BATTLE_PLAYER_COLS = {24, 35};
    /** 대전 키 칸에 보여 줄 키 이름의 최대 폭. 넘치면 옆 칸을 덮으므로 자른다. */
    private static final int BATTLE_KEY_WIDTH = 8;
    private static final int HELP_ROW = 14;
    private static final int MESSAGE_ROW = 17;
    private static final int KEY_VALUE_COL = 25;

    private final ScreenRouter router;
    /** 배경 구름이 흘러가도록 쌓는 시간. */
    private long clockNanos;
    private int cursor;
    private boolean editingKeys;
    private int keyCursor;
    private boolean editingBattleKeys;
    private int battleRow;
    /** 대전 키 페이지에서 고른 플레이어 (1 또는 2). */
    private int battlePlayer = 1;
    private GameCommand waitingFor;
    /** 새 키를 기다리는 대전 플레이어 (1, 2). 1인 게임 키를 기다리면 0. */
    private int waitingPlayer;
    private Kind confirming;
    private String message;
    private boolean messageIsError;

    public SettingsScreen(ScreenRouter router) {
        this.router = router;
    }

    /** 지금 보이는 페이지에서 선택된 줄. */
    public int cursor() {
        return editingKeys ? keyCursor : editingBattleKeys ? battleRow : cursor;
    }

    /** 대전 조작 키 페이지를 보고 있는가. */
    public boolean isEditingBattleKeys() {
        return editingBattleKeys;
    }

    /** 대전 키 페이지에서 고른 플레이어 (1 또는 2). */
    public int battlePlayer() {
        return battlePlayer;
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
            } else if (waitingPlayer == 0) {
                bind(waitingFor, key);
            } else {
                bindBattle(waitingPlayer, waitingFor, key);
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
        if (editingBattleKeys) {
            pressOnBattlePage(key);
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
                    waitingPlayer = 0;
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

    private void pressOnBattlePage(String key) {
        int rows = BATTLE_BACK_ROW + 1;
        switch (key) {
            case KeyNames.UP -> battleRow = Math.floorMod(battleRow - 1, rows);
            case KeyNames.DOWN -> battleRow = Math.floorMod(battleRow + 1, rows);
            case KeyNames.LEFT -> battlePlayer = 1;
            case KeyNames.RIGHT -> battlePlayer = 2;
            case KeyNames.ENTER -> {
                if (battleRow == BATTLE_BACK_ROW) {
                    editingBattleKeys = false;
                    message = null;
                } else if (battleRow == BATTLE_RESET_ROW) {
                    Settings current = router.settings();
                    save(current.withBattleKeys(BattleKeyBindings.defaultsAvoiding(
                            Settings.commonKeys(current.keyBindings()))), "대전 조작 키 기본값");
                } else {
                    // 이 단계를 연 Enter를 누르고 있으면 자동 반복이 곧바로 새 키(ENTER)로 처리되므로,
                    // 한 번 뗄 때까지 무시한다.
                    waitingFor = BattleKeyBindings.COMMANDS.get(battleRow);
                    waitingPlayer = battlePlayer;
                    message = null;
                    router.ignoreHeldKeys();
                }
            }
            case KeyNames.ESCAPE -> {
                editingBattleKeys = false;
                message = null;
            }
            default -> info("사용할 수 있는 키: ↑ ↓ ← → Enter Esc");
        }
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
                save(current.withDifficulty(value), "난이도 " + DisplayNames.of(value));
            }
            case SCREEN_SIZE -> {
                ScreenSize value = ScreenSize.values()[next];
                save(current.withScreenSize(value), "화면 크기 " + DisplayNames.of(value));
            }
            case COLOR_BLIND -> {
                boolean value = next == 1;
                save(current.withColorBlindMode(value), "색맹 모드 " + DisplayNames.onOff(value));
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
            case BATTLE_KEYS -> {
                editingBattleKeys = true;
                battleRow = 0;
                battlePlayer = 1;
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
            candidate = current.withKeyBindings(keys);
        } catch (IllegalArgumentException conflict) {
            error(conflictMessage(current, command, key));
            return;
        }
        save(candidate, COMMAND_LABELS.get(command) + " → " + KeyNames.display(key));
    }

    private void bindBattle(int player, GameCommand command, String key) {
        Settings current = router.settings();
        waitingFor = null;
        waitingPlayer = 0;
        Settings candidate;
        try {
            candidate = current.withBattleKeys(current.battleKeys().with(player, command, key));
        } catch (IllegalArgumentException conflict) {
            error(battleConflictMessage(current, key));
            return;
        }
        save(candidate, player + "P " + COMMAND_LABELS.get(command) + " → " + KeyNames.display(key));
    }

    /** 1인 게임 키를 바꿀 때 key를 이미 쓰는 조작. 공통 키는 대전 조작 키와도 겹칠 수 없다. */
    private static String conflictMessage(Settings settings, GameCommand command, String key) {
        for (GameCommand other : GameCommand.values()) {
            boolean together = other != command && (command != GameCommand.RESUME && other != GameCommand.RESUME
                    || command == GameCommand.QUIT_GAME || other == GameCommand.QUIT_GAME);
            if (together && key.equals(settings.keyBindings().get(other))) {
                return KeyNames.display(key) + ": " + quoted(COMMAND_LABELS.get(other)) + " 겹칩니다";
            }
        }
        var battle = settings.battleKeys().find(key);
        if (Settings.COMMON_COMMANDS.contains(command) && battle != null) {
            // 화면 폭(44칸)에 맞추려고 "대전"은 빼고 1P·2P로만 알린다.
            return KeyNames.display(key) + ": " + battle.getKey() + "P "
                    + quoted(COMMAND_LABELS.get(battle.getValue())) + " 겹칩니다";
        }
        return KeyNames.display(key) + ": 다른 조작과 겹치는 키입니다";
    }

    /** 조작 이름을 따옴표로 감싸고 받침에 맞는 조사(와/과)를 붙인다. */
    static String quoted(String label) {
        char last = label.charAt(label.length() - 1);
        boolean batchim = last >= '가' && last <= '힣' && (last - '가') % 28 != 0;
        return "'" + label + "'" + (batchim ? "과" : "와");
    }

    /** 대전 키를 바꿀 때 key를 이미 쓰는 조작: 다른 대전 키나 두 사람 공통 키. */
    private static String battleConflictMessage(Settings settings, String key) {
        var battle = settings.battleKeys().find(key);
        if (battle != null) {
            return KeyNames.display(key) + ": " + battle.getKey() + "P "
                    + quoted(COMMAND_LABELS.get(battle.getValue())) + " 겹칩니다";
        }
        for (GameCommand common : Settings.COMMON_COMMANDS) {
            if (key.equals(settings.keyBindings().get(common))) {
                return KeyNames.display(key) + ": 공통 " + quoted(COMMAND_LABELS.get(common)) + " 겹칩니다";
            }
        }
        return KeyNames.display(key) + ": 다른 조작과 겹치는 키입니다";
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
    public void update(long elapsedNanos) {
        clockNanos += elapsedNanos;
    }

    @Override
    public TextFrame render() {
        ColorPalette palette = router.palette();
        Settings settings = router.settings();
        TextFrame frame = TextFrame.blank(palette.base());
        MenuTheme.panels(frame, clockNanos, 3, 21);
        frame.putCentered(1, editingKeys ? "조작 키 설정" : editingBattleKeys ? "대전 조작 키" : "설정",
                palette.accentStyle());
        if (editingKeys) {
            renderKeyPage(frame, palette, settings);
        } else if (editingBattleKeys) {
            renderBattlePage(frame, palette, settings);
        } else {
            renderMainPage(frame, palette, settings);
        }

        if (waitingFor != null) {
            String target = (waitingPlayer == 0 ? "" : waitingPlayer + "P ") + COMMAND_LABELS.get(waitingFor);
            frame.putCentered(MESSAGE_ROW, "'" + target + "'에 쓸 키를 누르세요", palette.warningStyle());
            frame.putCentered(MESSAGE_ROW + 1, "Backspace 취소", palette.dimStyle());
        } else if (confirming != null) {
            frame.putCentered(MESSAGE_ROW,
                    confirming == Kind.CLEAR_SCORES ? "스코어보드를 초기화할까요?" : "모든 설정을 되돌릴까요?",
                    palette.warningStyle());
            frame.putCentered(MESSAGE_ROW + 1, "Enter 예   다른 키 아니오", palette.dimStyle());
        } else if (message != null) {
            frame.putCentered(MESSAGE_ROW, message, messageIsError ? palette.warningStyle() : palette.accentStyle());
        }
        // 아래 상자 안쪽(양옆 테두리 안)에 들어가도록 묶음 사이를 한 칸씩 띄운다.
        frame.putCentered(TextFrame.ROWS - 2, editingKeys
                ? "↑↓ 이동 · Enter 키 바꾸기 · Esc 뒤로"
                : editingBattleKeys
                ? "↑↓ 이동 ◄► 1P·2P Enter 변경 Esc 뒤로"
                : "◄► 변경 ↑↓ 이동 Enter 선택 Esc 뒤로", palette.dimStyle());
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
            if (row.kind() == Kind.KEYS || row.kind() == Kind.BATTLE_KEYS) {
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

    private void renderBattlePage(TextFrame frame, ColorPalette palette, Settings settings) {
        frame.put(BATTLE_PLAYER_COLS[0], LIST_TOP, "1P", palette.accentStyle());
        frame.put(BATTLE_PLAYER_COLS[1], LIST_TOP, "2P", palette.accentStyle());
        for (int i = 0; i <= BATTLE_BACK_ROW; i++) {
            int y = LIST_TOP + 1 + i + (i >= BATTLE_RESET_ROW ? 1 : 0);
            boolean selected = i == battleRow;
            TextStyle style = selected ? palette.highlightStyle() : palette.base();
            if (selected) {
                frame.fill(BATTLE_LABEL_COL - 2, y, TextFrame.COLUMNS - BATTLE_LABEL_COL, 1, style);
                frame.put(BATTLE_LABEL_COL - 2, y, "►", style);
            }
            if (i == BATTLE_RESET_ROW) {
                frame.put(BATTLE_LABEL_COL, y, "기본 키로 되돌리기", style);
                continue;
            }
            if (i == BATTLE_BACK_ROW) {
                frame.put(BATTLE_LABEL_COL, y, "돌아가기", style);
                continue;
            }
            GameCommand command = BattleKeyBindings.COMMANDS.get(i);
            frame.put(BATTLE_LABEL_COL, y, COMMAND_LABELS.get(command), style);
            for (int player = 1; player <= 2; player++) {
                boolean waiting = waitingFor == command && waitingPlayer == player;
                String key = waiting ? "..." : KeyNames.display(settings.battleKeys().of(player).get(command));
                if (key.length() > BATTLE_KEY_WIDTH) {
                    key = key.substring(0, BATTLE_KEY_WIDTH - 1) + "~";
                }
                // 고른 줄에서는 지금 고른 플레이어 칸을 [ ]로 감싼다.
                boolean chosen = selected && player == battlePlayer;
                frame.put(BATTLE_PLAYER_COLS[player - 1] - 1, y, chosen ? "[" + key + "]" : " " + key, style);
            }
        }
        Map<GameCommand, String> keys = settings.keyBindings();
        int noteRow = LIST_TOP + BATTLE_BACK_ROW + 4;
        frame.putCentered(noteRow, "두 사람 공통: " + KeyNames.display(keys.get(GameCommand.PAUSE)) + " 일시정지 · "
                + KeyNames.display(keys.get(GameCommand.QUIT_GAME)) + " 메뉴", palette.dimStyle());
        frame.putCentered(noteRow + 1, "공통 키는 '조작 키 설정'에서 바꿉니다", palette.dimStyle());
        if (!BattleKeyBindings.defaultsAvoiding(Settings.commonKeys(keys)).equals(BattleKeyBindings.standard())) {
            // 사용자의 공통 키가 기본 배치와 겹쳐 기본값 일부를 대체 키로 쓰고 있음을 알린다 (카드 18).
            frame.putCentered(noteRow + 2, "공통 키와 겹친 기본 키는 대체 키로", palette.warningStyle());
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
