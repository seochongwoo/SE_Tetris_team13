package team.tetris.ui.screen;

import java.awt.Color;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import team.tetris.core.TetrominoType;
import team.tetris.ui.ScreenRouter;
import team.tetris.ui.input.KeyNames;
import team.tetris.ui.render.Backdrop;
import team.tetris.ui.render.TextFrame;
import team.tetris.ui.render.TextStyle;
import team.tetris.ui.render.palette.ColorPalette;

/**
 * 시작 메뉴. 위쪽에 게임 이름, 그 아래 메뉴 항목을 ↑/↓로 고르고 Enter로 실행한다.
 *
 * <p>항목은 {@link MenuItem} 목록으로 받기 때문에, 메뉴를 추가하려면
 * {@link ScreenRouter#menuItems()}에 한 줄만 더하면 된다.
 */
public final class MenuScreen implements Screen {

    /** 메뉴 항목 하나: 화면에 보일 이름과 선택했을 때 할 일. */
    public record MenuItem(String label, Runnable action) {
        public MenuItem {
            Objects.requireNonNull(label, "label");
            Objects.requireNonNull(action, "action");
        }
    }

    private static final String TITLE = "TETRIS";
    private static final Map<Character, String[]> LETTERS = Map.of(
            'T', new String[] {"#####", "  #  ", "  #  ", "  #  ", "  #  "},
            'E', new String[] {"#####", "#    ", "#### ", "#    ", "#####"},
            'R', new String[] {"#### ", "#   #", "#### ", "#  # ", "#   #"},
            'I', new String[] {"#####", "  #  ", "  #  ", "  #  ", "#####"},
            'S', new String[] {" ####", "#    ", " ### ", "    #", "#### "});
    private static final TetrominoType[] TITLE_COLORS = {
        TetrominoType.Z, TetrominoType.L, TetrominoType.O, TetrominoType.S, TetrominoType.I, TetrominoType.T
    };
    /** 로고 맨 윗줄. 부제는 로고 바로 아래(7행 위)에 두어 대성당 첨탑과 떨어뜨린다. */
    private static final int TITLE_TOP = 1;
    /** 메뉴 패널: 왼쪽의 대성당을 가리지 않도록 오른쪽 하늘 영역에 둔다(격자 칸). */
    static final int PANEL_LEFT = 25;
    static final int PANEL_WIDTH = 18;
    private static final int PANEL_TOP = 9;
    /** 배경의 대성당이 서는 땅의 행. 패널은 이 위에서 끝난다. */
    private static final int GROUND_ROW = 21;
    /** 맨 아래 안내 줄. 창 아래 가장자리에 붙지 않게 한 줄 띄운다. */
    static final int HINT_ROW = TextFrame.ROWS - 2;
    /** 아래쪽 안내: 짝수 번째는 키캡으로 그릴 키 이름, 홀수 번째는 설명. */
    static final List<String> HINT_PARTS = List.of("↑", "", "↓", "이동 ·", "Enter", "선택");
    private static final Color HINT = new Color(208, 208, 208);
    /** 키캡 바탕색. 도트 화면에서는 윗변이 밝고 아랫변이 어두운 키캡으로 그려진다. */
    private static final Color KEYCAP = new Color(196, 198, 214);

    /** 하위 메뉴 제목과 항목 사이의 줄 수 (제목 한 줄 + 빈 줄). */
    private static final int HEADING_ROWS = 2;

    private final ScreenRouter router;
    private final List<MenuItem> items;
    /** 패널 맨 위에 보일 제목. 무엇을 고르는 화면인지 알려 준다. 시작 메뉴는 null. */
    private final String heading;
    /**
     * 새 묶음이 시작하는 항목 번호. 항목이 많아 한 줄 간격으로 붙을 때 묶음 사이에 빈 줄을 하나 둔다
     * (시작 메뉴: 1인 게임 / 대전 / 기타).
     */
    private final java.util.Set<Integer> groupStarts;
    /** Esc로 돌아갈 곳. 시작 메뉴처럼 돌아갈 곳이 없으면 null. */
    private final Runnable back;
    private int cursor;
    private boolean showKeyHint;
    private long clockNanos;

    public MenuScreen(ScreenRouter router, List<MenuItem> items) {
        this(router, items, (Runnable) null);
    }

    /** 묶음 사이를 띄우는 시작 메뉴. groupStarts는 새 묶음이 시작하는 항목 번호다. */
    public MenuScreen(ScreenRouter router, List<MenuItem> items, java.util.Set<Integer> groupStarts) {
        this(router, null, items, null, 0, groupStarts);
    }

    /** 하위 메뉴: back이 있으면 Esc로 돌아간다. */
    public MenuScreen(ScreenRouter router, List<MenuItem> items, Runnable back) {
        this(router, items, back, 0);
    }

    /** cursor 항목을 고른 상태로 시작하는 하위 메뉴 (기본값이 첫 항목이 아닐 때). */
    public MenuScreen(ScreenRouter router, List<MenuItem> items, Runnable back, int cursor) {
        this(router, null, items, back, cursor);
    }

    /** 제목이 있는 하위 메뉴 (예: 대전 모드 선택, 제한 시간). */
    public MenuScreen(ScreenRouter router, String heading, List<MenuItem> items, Runnable back, int cursor) {
        this(router, heading, items, back, cursor, java.util.Set.of());
    }

    private MenuScreen(ScreenRouter router, String heading, List<MenuItem> items, Runnable back, int cursor,
                       java.util.Set<Integer> groupStarts) {
        this.router = Objects.requireNonNull(router, "router");
        this.heading = heading;
        this.groupStarts = java.util.Set.copyOf(groupStarts);
        for (int start : this.groupStarts) {
            if (start <= 0 || start >= items.size()) {
                throw new IllegalArgumentException("Group must start inside the menu: " + start);
            }
        }
        this.back = back;
        this.items = List.copyOf(items);
        if (this.items.isEmpty()) {
            throw new IllegalArgumentException("Menu requires at least one item");
        }
        if (cursor < 0 || cursor >= this.items.size()) {
            throw new IllegalArgumentException("Cursor must point to an item: " + cursor);
        }
        this.cursor = cursor;
    }

    public int cursor() {
        return cursor;
    }

    public List<MenuItem> items() {
        return items;
    }

    public java.util.Optional<String> heading() {
        return java.util.Optional.ofNullable(heading);
    }

    @Override
    public void onKeyPressed(String key) {
        switch (key) {
            case KeyNames.UP -> move(-1);
            case KeyNames.DOWN -> move(1);
            case KeyNames.ENTER -> {
                showKeyHint = false;
                items.get(cursor).action().run();
            }
            case KeyNames.ESCAPE -> {
                if (back != null) {
                    back.run();
                } else {
                    showKeyHint = true;
                }
            }
            default -> showKeyHint = true;
        }
    }

    private void move(int delta) {
        showKeyHint = false;
        cursor = Math.floorMod(cursor + delta, items.size());
    }

    /** 배경 구름이 흘러가도록 시간을 쌓는다. */
    @Override
    public void update(long elapsedNanos) {
        clockNanos += elapsedNanos;
    }

    @Override
    public TextFrame render() {
        ColorPalette palette = router.palette();
        TextFrame frame = TextFrame.blank(palette.base());
        frame.setBackdrop(Backdrop.cathedral(clockNanos));
        drawTitle(frame, palette);
        TextStyle light = new TextStyle(HINT, palette.background(), false);
        frame.putCentered(TITLE_TOP + 5, "SE Tetris · Team 13", light);

        // 대성당은 왼쪽, 메뉴는 오른쪽 하늘의 패널 위에 둔다.
        boolean titled = heading != null;
        int itemsTop = rowOf(0);
        int panelRight = PANEL_LEFT + PANEL_WIDTH;
        int headerRows = titled ? HEADING_ROWS : 0;
        frame.addPlate(PANEL_LEFT, itemsTop - 1 - headerRows, PANEL_WIDTH, panelHeight(items.size(), gapRows()) + headerRows);
        if (titled) {
            frame.putCentered(PANEL_LEFT + 1, panelRight - 1, itemsTop - headerRows, heading, palette.accentStyle());
        }
        frame.addPlate(1, HINT_ROW, TextFrame.COLUMNS - 2, 1);
        for (int i = 0; i < items.size(); i++) {
            int row = rowOf(i);
            String label = items.get(i).label();
            if (i == cursor) {
                frame.fill(PANEL_LEFT + 1, row, PANEL_WIDTH - 2, 1, palette.highlightStyle());
                frame.put(PANEL_LEFT + 2, row, "►", palette.highlightStyle());
                frame.putCentered(PANEL_LEFT + 1, panelRight - 1, row, label, palette.highlightStyle());
            } else {
                frame.putCentered(PANEL_LEFT + 1, panelRight - 1, row, label, palette.base());
            }
        }

        router.notice().ifPresent(notice -> {
            int width = TextFrame.displayWidth(notice) + 2;
            frame.addPlate((TextFrame.COLUMNS - width) / 2, HINT_ROW - 1, width, 1);
            frame.putCentered(HINT_ROW - 1, notice, palette.warningStyle());
        });
        // 안내는 아래쪽 띠 한 줄. 다른 키를 누르면 같은 자리에 사용할 수 있는 키를 경고색으로 보여준다.
        if (showKeyHint) {
            frame.putCentered(HINT_ROW, "사용할 수 있는 키: ↑ ↓ Enter", palette.warningStyle());
        } else {
            drawKeyHint(frame, light, new TextStyle(palette.background(), KEYCAP, true));
        }
        return frame;
    }

    /** 키 이름은 양옆에 한 칸씩 띄운 키캡으로, 설명은 그 사이에 둔다. 전체를 가운데 정렬한다. */
    private static void drawKeyHint(TextFrame frame, TextStyle text, TextStyle keycap) {
        StringBuilder plain = new StringBuilder();
        for (int i = 0; i < HINT_PARTS.size(); i++) {
            String part = HINT_PARTS.get(i);
            if (!part.isEmpty()) {
                plain.append(i % 2 == 0 ? " " + part + " " : part).append(' ');
            }
        }
        int col = (TextFrame.COLUMNS - TextFrame.displayWidth(plain.toString().strip())) / 2;
        for (int i = 0; i < HINT_PARTS.size(); i++) {
            String part = HINT_PARTS.get(i);
            if (part.isEmpty()) {
                continue;
            }
            col = frame.put(col, HINT_ROW, i % 2 == 0 ? " " + part + " " : part, i % 2 == 0 ? keycap : text) + 1;
        }
    }

    /** 두 줄 간격으로 부제와 땅 사이에 들어가지 않으면 한 줄 간격으로 표시한다. */
    private static int itemSpacing(int itemCount) {
        int skyRows = GROUND_ROW - (TITLE_TOP + 6);
        return itemCount * 2 + 1 <= skyRows ? 2 : 1;
    }

    private static int panelHeight(int itemCount, int gapRows) {
        return (itemCount - 1) * itemSpacing(itemCount) + gapRows + 3; // 첫 항목과 위아래 여백 포함
    }

    /** 묶음 사이 빈 줄 수. 두 줄 간격일 때는 이미 띄어져 있으므로 넣지 않는다. */
    private int gapRows() {
        return itemSpacing(items.size()) == 1 ? groupStarts.size() : 0;
    }

    /** index번째 항목이 놓이는 행. */
    int rowOf(int index) {
        int gaps = 0;
        if (gapRows() > 0) {
            for (int start : groupStarts) {
                if (start <= index) {
                    gaps++;
                }
            }
        }
        return itemsTop(items.size(), heading != null, gapRows()) + index * itemSpacing(items.size()) + gaps;
    }

    /** 하늘 영역 안에서 메뉴를 세로로 가운데 정렬한다. */
    static int itemsTop(int itemCount) {
        return itemsTop(itemCount, false);
    }

    /** 제목이 있으면 제목 줄까지 포함한 패널 전체를 가운데 정렬한다. */
    static int itemsTop(int itemCount, boolean titled) {
        return itemsTop(itemCount, titled, 0);
    }

    private static int itemsTop(int itemCount, boolean titled, int gapRows) {
        int headerRows = titled ? HEADING_ROWS : 0;
        int panelRows = GROUND_ROW - PANEL_TOP;
        return PANEL_TOP + 1 + headerRows
                + Math.floorDiv(panelRows - panelHeight(itemCount, gapRows) - headerRows, 2);
    }

    private static void drawTitle(TextFrame frame, ColorPalette palette) {
        int letterWidth = 5;
        int totalWidth = TITLE.length() * (letterWidth + 1) - 1;
        int left = (TextFrame.COLUMNS - totalWidth) / 2;
        for (int i = 0; i < TITLE.length(); i++) {
            String[] rows = LETTERS.get(TITLE.charAt(i));
            int col = left + i * (letterWidth + 1);
            for (int row = 0; row < rows.length; row++) {
                for (int x = 0; x < letterWidth; x++) {
                    if (rows[row].charAt(x) == '#') {
                        frame.put(col + x, TITLE_TOP + row, "█", palette.blockStyle(TITLE_COLORS[i]));
                    }
                }
            }
        }
    }
}
