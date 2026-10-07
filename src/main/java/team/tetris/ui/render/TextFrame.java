package team.tetris.ui.render;

import java.util.Objects;

/**
 * 화면 한 장을 표현하는 고정 크기 글자 격자. 모든 화면은 이 격자에 글자를 찍고, Swing 쪽
 * ({@link TextFramePanel})은 격자를 그대로 그리기만 한다. 그래서 화면 로직은 Swing 없이 테스트할 수 있다.
 *
 * <p>한글처럼 폭이 넓은 글자는 두 칸을 차지한다. 첫 칸에 글자를, 둘째 칸에 이어짐 표시를 둔다.
 */
public final class TextFrame {

    /** 모든 화면이 공통으로 쓰는 격자 크기. */
    public static final int COLUMNS = 44;
    public static final int ROWS = 24;

    private static final int CONTINUATION = -1;

    private final int columns;
    private final int rows;
    private final int[][] codePoints;
    private final TextStyle[][] styles;

    public TextFrame(int columns, int rows, TextStyle base) {
        if (columns <= 0 || rows <= 0) {
            throw new IllegalArgumentException("Frame size must be positive");
        }
        Objects.requireNonNull(base, "base");
        this.columns = columns;
        this.rows = rows;
        this.codePoints = new int[rows][columns];
        this.styles = new TextStyle[rows][columns];
        for (int row = 0; row < rows; row++) {
            for (int col = 0; col < columns; col++) {
                codePoints[row][col] = ' ';
                styles[row][col] = base;
            }
        }
    }

    public static TextFrame blank(TextStyle base) {
        return new TextFrame(COLUMNS, ROWS, base);
    }

    public int columns() {
        return columns;
    }

    public int rows() {
        return rows;
    }

    /**
     * (col, row)부터 text를 찍고, 다음 글자가 올 열을 반환한다. 격자 밖으로 나가는 부분은 잘린다.
     */
    public int put(int col, int row, String text, TextStyle style) {
        Objects.requireNonNull(text, "text");
        Objects.requireNonNull(style, "style");
        int x = col;
        for (int i = 0; i < text.length(); ) {
            int codePoint = text.codePointAt(i);
            i += Character.charCount(codePoint);
            int width = isWide(codePoint) ? 2 : 1;
            if (row >= 0 && row < rows && x >= 0 && x + width <= columns) {
                write(x, row, codePoint, width, style);
            }
            x += width;
        }
        return x;
    }

    public void putCentered(int row, String text, TextStyle style) {
        putCentered(0, columns, row, text, style);
    }

    /** [fromCol, toCol) 구간 가운데에 text를 찍는다. */
    public void putCentered(int fromCol, int toCol, int row, String text, TextStyle style) {
        int width = displayWidth(text);
        put(fromCol + Math.max(0, (toCol - fromCol - width) / 2), row, text, style);
    }

    /** text의 마지막 글자가 endCol 바로 앞에서 끝나도록 오른쪽 정렬해서 찍는다. */
    public void putRight(int endCol, int row, String text, TextStyle style) {
        put(endCol - displayWidth(text), row, text, style);
    }

    /** 사각형 영역을 공백으로 채운다 (배경색 칠하기용). */
    public void fill(int col, int row, int width, int height, TextStyle style) {
        for (int y = row; y < row + height; y++) {
            for (int x = col; x < col + width; x++) {
                put(x, y, " ", style);
            }
        }
    }

    /** 테두리 상자를 그리고 안쪽을 inside 스타일 공백으로 채운다. */
    public void box(int col, int row, int width, int height, TextStyle border, TextStyle inside) {
        if (width < 2 || height < 2) {
            throw new IllegalArgumentException("Box must be at least 2x2");
        }
        fill(col, row, width, height, inside);
        int right = col + width - 1;
        int bottom = row + height - 1;
        put(col, row, "┌", border);
        put(right, row, "┐", border);
        put(col, bottom, "└", border);
        put(right, bottom, "┘", border);
        for (int x = col + 1; x < right; x++) {
            put(x, row, "─", border);
            put(x, bottom, "─", border);
        }
        for (int y = row + 1; y < bottom; y++) {
            put(col, y, "│", border);
            put(right, y, "│", border);
        }
    }

    /** (col, row)의 글자. 넓은 글자의 둘째 칸이면 0. */
    public int codePointAt(int col, int row) {
        int value = codePoints[row][col];
        return value == CONTINUATION ? 0 : value;
    }

    /** 넓은 글자의 둘째 칸인가. */
    public boolean isContinuation(int col, int row) {
        return codePoints[row][col] == CONTINUATION;
    }

    public TextStyle styleAt(int col, int row) {
        return styles[row][col];
    }

    /** 한 행을 문자열로 (넓은 글자는 한 번만). 테스트와 디버깅용. */
    public String rowText(int row) {
        StringBuilder text = new StringBuilder();
        for (int col = 0; col < columns; col++) {
            if (codePoints[row][col] != CONTINUATION) {
                text.appendCodePoint(codePoints[row][col]);
            }
        }
        return text.toString();
    }

    /** 전체를 행 단위로 이은 문자열. 테스트와 디버깅용. */
    public String text() {
        StringBuilder text = new StringBuilder();
        for (int row = 0; row < rows; row++) {
            text.append(rowText(row)).append('\n');
        }
        return text.toString();
    }

    /** 격자에서 차지하는 칸 수 (넓은 글자는 2칸). */
    public static int displayWidth(String text) {
        int width = 0;
        for (int i = 0; i < text.length(); ) {
            int codePoint = text.codePointAt(i);
            i += Character.charCount(codePoint);
            width += isWide(codePoint) ? 2 : 1;
        }
        return width;
    }

    /**
     * 한글·한자·전각 기호처럼 고정폭 글꼴에서 두 칸을 차지하는 글자인가. 화살표(←↑→↓)는 한국어
     * 글꼴에서 두 칸 폭으로 그려지므로 함께 넓은 글자로 취급한다.
     */
    public static boolean isWide(int codePoint) {
        return (codePoint >= 0x1100 && codePoint <= 0x115F)
                || (codePoint >= 0x2190 && codePoint <= 0x21FF)
                || (codePoint >= 0x2E80 && codePoint <= 0x303E)
                || (codePoint >= 0x3041 && codePoint <= 0x33FF)
                || (codePoint >= 0x3400 && codePoint <= 0x4DBF)
                || (codePoint >= 0x4E00 && codePoint <= 0x9FFF)
                || (codePoint >= 0xA960 && codePoint <= 0xA97F)
                || (codePoint >= 0xAC00 && codePoint <= 0xD7A3)
                || (codePoint >= 0xF900 && codePoint <= 0xFAFF)
                || (codePoint >= 0xFE30 && codePoint <= 0xFE4F)
                || (codePoint >= 0xFF00 && codePoint <= 0xFF60)
                || (codePoint >= 0xFFE0 && codePoint <= 0xFFE6);
    }

    private void write(int x, int row, int codePoint, int width, TextStyle style) {
        // 넓은 글자의 절반만 덮어쓰게 되면 남은 절반을 공백으로 정리한다.
        if (codePoints[row][x] == CONTINUATION && x > 0) {
            codePoints[row][x - 1] = ' ';
        }
        if (x + width < columns && codePoints[row][x + width] == CONTINUATION) {
            codePoints[row][x + width] = ' ';
        }
        codePoints[row][x] = codePoint;
        styles[row][x] = style;
        if (width == 2) {
            codePoints[row][x + 1] = CONTINUATION;
            styles[row][x + 1] = style;
        }
    }
}
