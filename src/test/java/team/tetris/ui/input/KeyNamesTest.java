package team.tetris.ui.input;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.event.KeyEvent;
import org.junit.jupiter.api.Test;

class KeyNamesTest {

    @Test
    void mapsKeyCodesToStableIdentifiersIndependentOfOsLanguage() {
        assertEquals("LEFT", KeyNames.nameOf(KeyEvent.VK_LEFT));
        assertEquals("SPACE", KeyNames.nameOf(KeyEvent.VK_SPACE));
        assertEquals("ESCAPE", KeyNames.nameOf(KeyEvent.VK_ESCAPE));
        assertEquals("ENTER", KeyNames.nameOf(KeyEvent.VK_ENTER));
        assertEquals("BACK_SPACE", KeyNames.nameOf(KeyEvent.VK_BACK_SPACE));
        assertEquals("P", KeyNames.nameOf(KeyEvent.VK_P));
    }

    @Test
    void digitKeysBecomeDigitPrefixedSoTheyFitTheSettingsFormat() {
        assertEquals("DIGIT1", KeyNames.nameOf(KeyEvent.VK_1));
        assertEquals("1", KeyNames.display("DIGIT1"));
    }

    @Test
    void unknownKeyCodeHasNoName() {
        assertNull(KeyNames.nameOf(-12345));
    }

    @Test
    void everyProducedNameIsAValidSettingsKeyIdentifier() {
        for (int code = 0; code <= 0xFFFF; code++) {
            String name = KeyNames.nameOf(code);
            if (name != null) {
                assertTrue(name.matches("[A-Z][A-Z0-9_]*"), name);
            }
        }
    }

    @Test
    void displayUsesShortSymbolsForCommonKeys() {
        assertEquals("←", KeyNames.display("LEFT"));
        assertEquals("↑", KeyNames.display("UP"));
        assertEquals("Esc", KeyNames.display("ESCAPE"));
        assertEquals("Space", KeyNames.display("SPACE"));
        assertEquals("F5", KeyNames.display("F5"));
        assertEquals("-", KeyNames.display(null));
    }
}
