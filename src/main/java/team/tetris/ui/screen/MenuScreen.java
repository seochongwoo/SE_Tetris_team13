package team.tetris.ui.screen;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import team.tetris.core.TetrominoType;
import team.tetris.ui.ScreenRouter;
import team.tetris.ui.input.KeyNames;
import team.tetris.ui.render.TextFrame;
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
    private static final int TITLE_TOP = 2;
    private static final int ITEMS_TOP = 11;

    private final ScreenRouter router;
    private final List<MenuItem> items;
    private int cursor;
    private boolean showKeyHint;

    public MenuScreen(ScreenRouter router, List<MenuItem> items) {
        this.router = Objects.requireNonNull(router, "router");
        this.items = List.copyOf(items);
        if (this.items.isEmpty()) {
            throw new IllegalArgumentException("Menu requires at least one item");
        }
    }

    public int cursor() {
        return cursor;
    }

    public List<MenuItem> items() {
        return items;
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
            default -> showKeyHint = true;
        }
    }

    private void move(int delta) {
        showKeyHint = false;
        cursor = Math.floorMod(cursor + delta, items.size());
    }

    @Override
    public TextFrame render() {
        ColorPalette palette = router.palette();
        TextFrame frame = TextFrame.blank(palette.base());
        drawTitle(frame, palette);
        frame.putCentered(TITLE_TOP + 6, "SE Tetris · Team 13", palette.dimStyle());

        for (int i = 0; i < items.size(); i++) {
            int row = ITEMS_TOP + i * 2;
            String label = items.get(i).label();
            if (i == cursor) {
                String text = " ► " + label + "   ";
                int width = TextFrame.displayWidth(text);
                frame.put((TextFrame.COLUMNS - width) / 2, row, text, palette.highlightStyle());
            } else {
                frame.putCentered(row, label, palette.base());
            }
        }

        router.notice().ifPresent(notice ->
                frame.putCentered(TextFrame.ROWS - 4, notice, palette.warningStyle()));
        if (showKeyHint) {
            frame.putCentered(TextFrame.ROWS - 3, "사용할 수 있는 키: ↑ ↓ Enter", palette.warningStyle());
        }
        frame.putCentered(TextFrame.ROWS - 2, "↑↓ 이동   Enter 선택", palette.dimStyle());
        return frame;
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
