package team.tetris.ui.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Color;
import org.junit.jupiter.api.Test;

class TextFrameTest {

    private static final TextStyle BASE = new TextStyle(Color.WHITE, Color.BLACK, false);
    private static final TextStyle RED = new TextStyle(Color.RED, Color.BLACK, true);

    @Test
    void putWritesTextAndReturnsTheNextColumn() {
        TextFrame frame = new TextFrame(10, 2, BASE);

        int next = frame.put(2, 0, "abc", RED);

        assertEquals(5, next);
        assertEquals("  abc     ", frame.rowText(0));
        assertEquals(RED, frame.styleAt(3, 0));
        assertEquals(BASE, frame.styleAt(0, 0));
    }

    @Test
    void wideCharactersTakeTwoCells() {
        TextFrame frame = new TextFrame(10, 1, BASE);

        int next = frame.put(0, 0, "가A", BASE);

        assertEquals(3, next);
        assertEquals('가', frame.codePointAt(0, 0));
        assertTrue(frame.isContinuation(1, 0));
        assertEquals(0, frame.codePointAt(1, 0));
        assertEquals('A', frame.codePointAt(2, 0));
        assertEquals(3, TextFrame.displayWidth("가A"));
        assertTrue(frame.rowText(0).startsWith("가A"));
    }

    @Test
    void overwritingHalfOfAWideCharacterClearsTheOtherHalf() {
        TextFrame frame = new TextFrame(4, 1, BASE);
        frame.put(0, 0, "가", BASE);

        frame.put(1, 0, "B", BASE);

        assertEquals(' ', frame.codePointAt(0, 0));
        assertEquals('B', frame.codePointAt(1, 0));
        assertFalse(frame.isContinuation(1, 0));
    }

    @Test
    void textOutsideTheFrameIsClippedWithoutErrors() {
        TextFrame frame = new TextFrame(3, 1, BASE);

        frame.put(-1, 0, "xyz", BASE);
        frame.put(2, 0, "가", BASE);
        frame.put(0, 5, "nope", BASE);

        assertEquals("yz ", frame.rowText(0));
    }

    @Test
    void centeredAndRightAlignedText() {
        TextFrame frame = new TextFrame(10, 2, BASE);

        frame.putCentered(0, "ab", BASE);
        frame.putRight(10, 1, "xy", BASE);

        assertEquals("    ab    ", frame.rowText(0));
        assertEquals("        xy", frame.rowText(1));
    }

    @Test
    void boxDrawsCornersAndEdgesAndClearsInside() {
        TextFrame frame = new TextFrame(5, 3, BASE);
        frame.put(2, 1, "Q", BASE);

        frame.box(0, 0, 5, 3, RED, BASE);

        assertEquals("┌───┐", frame.rowText(0));
        assertEquals("│   │", frame.rowText(1));
        assertEquals("└───┘", frame.rowText(2));
        assertThrows(IllegalArgumentException.class, () -> frame.box(0, 0, 1, 3, RED, BASE));
    }

    @Test
    void rejectsEmptyFrames() {
        assertThrows(IllegalArgumentException.class, () -> new TextFrame(0, 1, BASE));
    }

    @Test
    void standardFrameHasTheSharedSize() {
        TextFrame frame = TextFrame.blank(BASE);

        assertEquals(TextFrame.COLUMNS, frame.columns());
        assertEquals(TextFrame.ROWS, frame.rows());
        assertEquals(TextFrame.ROWS, frame.text().split("\n").length);
    }
}
