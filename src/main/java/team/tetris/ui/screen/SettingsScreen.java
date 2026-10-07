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
import team.tetris.ui.ScreenRouter;
import team.tetris.ui.input.KeyNames;
import team.tetris.ui.render.TextFrame;
import team.tetris.ui.render.TextStyle;
import team.tetris.ui.render.palette.ColorPalette;

/**
 * 설정 화면: 화면 크기, 색맹 모드, 난이도, 조작 키 변경, 스코어보드 초기화, 기본 설정 복원.
 * 바꾼 값은 그 자리에서 저장되고, 저장에 성공했을 때만 화면에 적용된다.
 *
 * <p>키를 바꿀 때는 해당 줄에서 Enter를 누른 뒤 새 키를 누른다 (Backspace는 취소). 다른 조작과
 * 겹치는 키는 설정 모델이 거부하므로 그 결과를 그대로 안내한다.
 */
public final class SettingsScreen implements Screen {

    private enum Kind { SCREEN_SIZE, COLOR_BLIND, DIFFICULTY, KEY, CLEAR_SCORES, RESET, BACK }

    private record Row(Kind kind, GameCommand command, String label) {
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

    private static final List<Row> ROWS = buildRows();
    private static final int LIST_TOP = 3;
    private static final int LABEL_COL = 4;
    private static final int VALUE_COL = 25;

    private final ScreenRouter router;
    private int cursor;
    private GameCommand waitingFor;
    private Kind confirming;
    private String message;
    private boolean messageIsError;

    public SettingsScreen(ScreenRouter router) {
        this.router = router;
    }

    private static List<Row> buildRows() {
        List<Row> rows = new ArrayList<>();
        rows.add(new Row(Kind.SCREEN_SIZE, null, "화면 크기"));
        rows.add(new Row(Kind.COLOR_BLIND, null, "색맹 모드"));
        rows.add(new Row(Kind.DIFFICULTY, null, "난이도"));
        for (GameCommand command : GameCommand.values()) {
            rows.add(new Row(Kind.KEY, command, COMMAND_LABELS.get(command)));
        }
        rows.add(new Row(Kind.CLEAR_SCORES, null, "스코어보드 초기화"));
        rows.add(new Row(Kind.RESET, null, "기본 설정으로 되돌리기"));
        rows.add(new Row(Kind.BACK, null, "돌아가기"));
        return List.copyOf(rows);
    }

    public int cursor() {
        return cursor;
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
        switch (key) {
            case KeyNames.UP -> cursor = Math.floorMod(cursor - 1, ROWS.size());
            case KeyNames.DOWN -> cursor = Math.floorMod(cursor + 1, ROWS.size());
            case KeyNames.LEFT -> adjust(-1);
            case KeyNames.RIGHT -> adjust(1);
            case KeyNames.ENTER -> activate();
            case KeyNames.ESCAPE -> router.showMenu();
            default -> info("사용할 수 있는 키: ↑ ↓ ← → Enter Esc");
        }
    }

    private void adjust(int direction) {
        Settings current = router.settings();
        switch (ROWS.get(cursor).kind()) {
            case SCREEN_SIZE -> {
                ScreenSize[] sizes = ScreenSize.values();
                ScreenSize next = sizes[Math.floorMod(current.screenSize().ordinal() + direction, sizes.length)];
                save(new Settings(next, current.keyBindings(), current.colorBlindMode(), current.difficulty()), "화면 크기 " + sizeLabel(next));
            }
            case COLOR_BLIND -> {
                boolean next = !current.colorBlindMode();
                save(new Settings(current.screenSize(), current.keyBindings(), next, current.difficulty()), "색맹 모드 " + onOff(next));
            }
            case DIFFICULTY -> {
                Difficulty[] choices = Difficulty.values();
                Difficulty next = choices[Math.floorMod(current.difficulty().ordinal() + direction, choices.length)];
                save(new Settings(current.screenSize(), current.keyBindings(), current.colorBlindMode(), next),
                        "난이도 " + next.name());
            }
            default -> {
            }
        }
    }

    private void activate() {
        Row row = ROWS.get(cursor);
        switch (row.kind()) {
            case SCREEN_SIZE, COLOR_BLIND, DIFFICULTY -> adjust(1);
            // 이 단계를 연 Enter를 누르고 있으면 자동 반복이 곧바로 새 키(ENTER)나 "예"로 처리되므로,
            // 한 번 뗄 때까지 무시한다.
            case KEY -> {
                waitingFor = row.command();
                message = null;
                router.ignoreHeldKeys();
            }
            case CLEAR_SCORES, RESET -> {
                confirming = row.kind();
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

    @Override
    public TextFrame render() {
        ColorPalette palette = router.palette();
        Settings settings = router.settings();
        TextFrame frame = TextFrame.blank(palette.base());
        frame.putCentered(1, "설정", palette.accentStyle());

        for (int i = 0; i < ROWS.size(); i++) {
            Row row = ROWS.get(i);
            int y = LIST_TOP + i;
            boolean selected = i == cursor;
            TextStyle style = selected ? palette.highlightStyle() : palette.base();
            if (selected) {
                frame.fill(LABEL_COL - 2, y, TextFrame.COLUMNS - LABEL_COL, 1, style);
                frame.put(LABEL_COL - 2, y, "►", style);
            }
            frame.put(LABEL_COL, y, row.label(), style);
            String value = valueOf(row, settings, selected);
            if (!value.isEmpty()) {
                frame.put(VALUE_COL, y, value, style);
            }
        }

        int messageRow = LIST_TOP + ROWS.size() + 1;
        if (waitingFor != null) {
            frame.putCentered(messageRow, "'" + COMMAND_LABELS.get(waitingFor) + "'에 쓸 키를 누르세요",
                    palette.warningStyle());
            frame.putCentered(messageRow + 1, "Backspace 취소", palette.dimStyle());
        } else if (confirming != null) {
            frame.putCentered(messageRow,
                    confirming == Kind.CLEAR_SCORES ? "스코어보드를 초기화할까요?" : "모든 설정을 되돌릴까요?",
                    palette.warningStyle());
            frame.putCentered(messageRow + 1, "Enter 예   다른 키 아니오", palette.dimStyle());
        } else if (message != null) {
            frame.putCentered(messageRow, message, messageIsError ? palette.warningStyle() : palette.accentStyle());
        }
        frame.putCentered(TextFrame.ROWS - 2, "↑↓ 이동  ←→ 변경  Enter 선택  Esc 뒤로", palette.dimStyle());
        return frame;
    }

    private String valueOf(Row row, Settings settings, boolean selected) {
        return switch (row.kind()) {
            case SCREEN_SIZE -> selected ? "◄ " + sizeLabel(settings.screenSize()) + " ►" : sizeLabel(settings.screenSize());
            case COLOR_BLIND -> onOff(settings.colorBlindMode());
            case DIFFICULTY -> settings.difficulty().name();
            case KEY -> waitingFor == row.command() ? "..." : KeyNames.display(settings.keyBindings().get(row.command()));
            default -> "";
        };
    }

    private static String sizeLabel(ScreenSize size) {
        return switch (size) {
            case SMALL -> "작게";
            case MEDIUM -> "보통";
            case LARGE -> "크게";
        };
    }

    private static String onOff(boolean value) {
        return value ? "켜짐" : "꺼짐";
    }
}
