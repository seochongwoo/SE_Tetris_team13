package team.tetris.ui.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Color;
import java.awt.Rectangle;
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

    @Test
    void tileKeepsItsGlyphInTheTextAndCoversTwoCells() {
        TextFrame frame = new TextFrame(6, 1, BASE);
        BlockTile tile = new BlockTile(BlockTile.Kind.SOLID, Color.RED, BlockTile.Pattern.DOT, null);

        frame.putTile(2, 0, "##", RED, tile);

        assertEquals("  ##  ", frame.rowText(0));
        assertEquals(tile, frame.tileAt(2, 0));
        assertTrue(frame.isTileContinuation(3, 0));
        assertFalse(frame.isTileContinuation(2, 0));
        assertEquals(null, frame.tileAt(3, 0));
    }

    @Test
    void writingOverEitherHalfOfATileRemovesIt() {
        TextFrame frame = new TextFrame(6, 1, BASE);
        BlockTile tile = new BlockTile(BlockTile.Kind.EMPTY, Color.GRAY, BlockTile.Pattern.NONE, null);
        frame.putTile(0, 0, "・", BASE, tile);
        frame.putTile(2, 0, "・", BASE, tile);

        frame.put(0, 0, "x", BASE);
        frame.put(3, 0, "y", BASE);

        assertEquals(null, frame.tileAt(0, 0));
        assertEquals(null, frame.tileAt(2, 0));
        assertFalse(frame.isTileContinuation(1, 0));
    }

    @Test
    void tileOutsideTheFrameIsIgnored() {
        TextFrame frame = new TextFrame(3, 1, BASE);
        BlockTile tile = new BlockTile(BlockTile.Kind.SOLID, Color.RED, BlockTile.Pattern.DOT, null);

        frame.putTile(2, 0, "##", RED, tile);
        frame.putTile(0, 1, "##", RED, tile);

        assertEquals(null, frame.tileAt(2, 0));
    }

    @Test
    void shadeDarkensWhatIsAlreadyDrawnButNotWhatComesAfter() {
        TextFrame frame = new TextFrame(4, 2, BASE);
        frame.put(0, 0, "ab", BASE);

        frame.shade();
        frame.put(1, 1, "c", RED);

        assertTrue(frame.isShaded(0, 0));
        assertTrue(frame.isShaded(3, 1));
        assertFalse(frame.isShaded(1, 1));
    }

    @Test
    void frameHasNoBackdropUntilAScreenSetsOne() {
        TextFrame frame = new TextFrame(4, 2, BASE);
        assertEquals(null, frame.backdrop());

        frame.setBackdrop(Backdrop.cathedral(5));

        assertEquals(Backdrop.cathedral(5), frame.backdrop());
    }

    @Test
    void backdropKeepsItsOwnCopyOfTheHole() {
        Rectangle hole = new Rectangle(1, 1, 4, 4);
        Backdrop backdrop = Backdrop.drift(0, 2, hole);

        hole.width = 99;
        backdrop.hole().height = 99;

        assertEquals(new Rectangle(1, 1, 4, 4), backdrop.hole());
        assertEquals(null, Backdrop.cathedral(0).hole());
    }

    @Test
    void platesAreKeptInOrderAndCopiedOut() {
        TextFrame frame = new TextFrame(10, 5, BASE);
        frame.addPlate(1, 2, 3, 1);
        frame.addPlate(0, 4, 10, 1);

        frame.plates().get(0).width = 99;

        assertEquals(java.util.List.of(new Rectangle(1, 2, 3, 1), new Rectangle(0, 4, 10, 1)), frame.plates());
    }
}
