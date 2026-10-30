package team.tetris.ui.screen;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import team.tetris.core.TetrominoType;
import team.tetris.ui.render.TextFrame;
import team.tetris.ui.render.TextRenderer;
import team.tetris.ui.render.TextStyle;
import team.tetris.ui.render.palette.ColorPalette;

/**
 * 게임 화면 위에 잠깐 띄우는 축하 문구. 한 번에 4줄 이상 지우면 "TETRIS!", 레벨이 오르면 "LEVEL UP"을
 * 보드 가운데에 띄운다. 문구는 아래에서 살짝 떠오른 뒤 머물다가 깜빡이며 사라진다.
 *
 * <p>게임 진행 중에만 시간이 흐르므로 일시정지 동안에는 문구도 멈춘다. 지운 줄 수와 레벨만 받아 판단하므로
 * Swing이나 세션 없이 테스트할 수 있다.
 */
final class GameBanner {

    static final long DURATION_NANOS = 1_400_000_000L;
    static final String TETRIS = "T E T R I S !";
    static final int TETRIS_LINES = 4;
    private static final long RISE_NANOS = 250_000_000L;
    private static final long BLINK_NANOS = 100_000_000L;
    private static final long FADE_FROM_NANOS = DURATION_NANOS - 4 * BLINK_NANOS;
    private static final int RISE_ROWS = 2;
    /** 첫 문구가 머무는 행. 두 번째 문구는 그 아래 두 줄에 둔다. */
    static final int TOP_ROW = TextRenderer.rowOf(6);
    private static final int WIDTH = 20;
    private static final TetrominoType[] TETRIS_COLORS = {
        TetrominoType.I, TetrominoType.O, TetrominoType.T, TetrominoType.S,
        TetrominoType.Z, TetrominoType.J, TetrominoType.L
    };

    private record Shown(String text, boolean tetris, long startNanos) {
    }

    private final List<Shown> shown = new ArrayList<>();
    private int lastLines;
    private int lastLevel;
    private long clockNanos;

    GameBanner(int clearedLines, int level) {
        lastLines = clearedLines;
        lastLevel = level;
    }

    /** 게임이 진행된 시간만큼 문구 시간을 흘린다. */
    void advance(long elapsedNanos) {
        clockNanos += elapsedNanos;
        shown.removeIf(banner -> clockNanos - banner.startNanos() >= DURATION_NANOS);
    }

    /** 최신 진행 상황을 보고 새 문구를 띄운다. */
    void observe(int clearedLines, int level) {
        if (clearedLines - lastLines >= TETRIS_LINES) {
            show(TETRIS, true);
        }
        if (level > lastLevel) {
            show("LEVEL UP!  Lv " + level, false);
        }
        lastLines = clearedLines;
        lastLevel = level;
    }

    private void show(String text, boolean tetris) {
        shown.removeIf(banner -> banner.tetris() == tetris);
        shown.add(new Shown(text, tetris, clockNanos));
    }

    /** 지금 보이는 문구 (테스트용). */
    List<String> texts() {
        return shown.stream().filter(this::visible).map(Shown::text).toList();
    }

    void draw(TextFrame frame, ColorPalette palette) {
        TextStyle plate = new TextStyle(palette.accent(), plateColor(palette), true);
        for (int i = 0; i < shown.size(); i++) {
            Shown banner = shown.get(i);
            if (!visible(banner)) {
                continue;
            }
            long age = clockNanos - banner.startNanos();
            int rise = (int) Math.max(0, RISE_ROWS - age * (RISE_ROWS + 1) / RISE_NANOS);
            int row = TOP_ROW + 2 * i + rise;
            int left = TextRenderer.BOARD_LEFT + 1 + (TextRenderer.CELL_WIDTH * 10 - WIDTH) / 2;
            frame.fill(left, row, WIDTH, 1, plate);
            int col = left + (WIDTH - TextFrame.displayWidth(banner.text())) / 2;
            if (banner.tetris()) {
                int color = 0;
                for (char letter : banner.text().toCharArray()) {
                    TextStyle style = letter == ' ' ? plate
                            : plate.withForeground(palette.block(TETRIS_COLORS[color++ % TETRIS_COLORS.length]));
                    col = frame.put(col, row, String.valueOf(letter), style);
                }
            } else {
                frame.put(col, row, banner.text(), plate);
            }
        }
    }

    /** 사라지기 직전에는 깜빡인다. */
    private boolean visible(Shown banner) {
        long age = clockNanos - banner.startNanos();
        return age < FADE_FROM_NANOS || ((age - FADE_FROM_NANOS) / BLINK_NANOS) % 2 == 0;
    }

    /** 블록 위에서도 글자가 읽히도록 배경보다 조금 밝은 판을 깐다. */
    private static Color plateColor(ColorPalette palette) {
        Color base = palette.background();
        return new Color(Math.min(255, base.getRed() + 26), Math.min(255, base.getGreen() + 28),
                Math.min(255, base.getBlue() + 40));
    }
}
