package at.koopro.wizardsandbeasts.client.ui;

import at.koopro.wizardsandbeasts.client.gui.util.UiContrast;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guards the identity colours that are actually drawn as glyphs. The failing cases here are the real
 * ones reported in-game: pale heritage tints on parchment and dark lineage / category tints on a dark
 * panel, both of which rendered as near-invisible text before the clamp.
 */
class UiContrastTest {

    private static final int PARCHMENT = 0xFFE1CC9D;
    private static final int CHIP_SELECTED = 0xFF3A2B17;
    private static final int SPELL_PANEL = 0xFF1E1A32;

    @Test
    void ratio_matchesKnownExtremes() {
        assertEquals(21.0, UiContrast.ratio(0xFFFFFFFF, 0xFF000000), 0.01);
        assertEquals(1.0, UiContrast.ratio(0xFF8B00FF, 0xFF8B00FF), 0.001);
    }

    @Test
    void readableOn_leavesAlreadyLegibleColoursAlone() {
        // Dark ink on parchment and the pale title cream on the dark panel both already clear AA.
        assertEquals(0xFF1B130A, UiContrast.readableOn(0xFF1B130A, PARCHMENT));
        assertEquals(0xFFF1E0B2, UiContrast.readableOn(0xFFF1E0B2, SPELL_PANEL));
    }

    @Test
    void readableOn_darkensPaleHeritagesAgainstParchment() {
        // Veela (0xFFCCEE) and Wizard (0xAA88FF) were the invisible ones on the detail panel.
        for (int signature : new int[] {0xFFFFCCEE, 0xFFAA88FF, 0xFF88AACC}) {
            int fixed = UiContrast.readableOn(signature, PARCHMENT);
            assertTrue(UiContrast.ratio(fixed, PARCHMENT) >= UiContrast.AA_TEXT,
                    () -> String.format("%08X still unreadable on parchment", signature));
            assertTrue(UiContrast.luminance(fixed) < UiContrast.luminance(signature),
                    () -> String.format("%08X should have been darkened", signature));
        }
    }

    @Test
    void readableOn_lightensDarkLineagesAgainstTheSelectedChip() {
        // Pure-blood (0x220042) and Metamorphmagus (0x3F244D) on the selected chip's dark fill.
        for (int accent : new int[] {0xFF220042, 0xFF3F244D, 0xFF6A3018}) {
            int fixed = UiContrast.readableOn(accent, CHIP_SELECTED);
            assertTrue(UiContrast.ratio(fixed, CHIP_SELECTED) >= UiContrast.AA_TEXT,
                    () -> String.format("%08X still unreadable on the selected chip", accent));
            assertTrue(UiContrast.luminance(fixed) > UiContrast.luminance(accent),
                    () -> String.format("%08X should have been lightened", accent));
        }
    }

    @Test
    void readableOn_lightensDarkArtsAgainstTheSpellPanel() {
        int fixed = UiContrast.readableOn(0xFF8B00FF, SPELL_PANEL);
        assertTrue(UiContrast.ratio(0xFF8B00FF, SPELL_PANEL) < UiContrast.AA_TEXT,
                "precondition: the raw Dark Arts purple must be the unreadable case");
        assertTrue(UiContrast.ratio(fixed, SPELL_PANEL) >= UiContrast.AA_TEXT);
    }

    @Test
    void readableOn_alwaysReturnsOpaque() {
        assertEquals(0xFF000000, UiContrast.readableOn(0x00000000, 0xFFFFFFFF) & 0xFF000000);
    }
}
