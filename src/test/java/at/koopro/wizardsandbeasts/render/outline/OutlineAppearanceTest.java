package at.koopro.wizardsandbeasts.render.outline;

import net.minecraft.util.ARGB;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** What an outline looks like: the style every spell outline shares, and its last-second fade. */
class OutlineAppearanceTest {

    // ── style ──

    @Test
    void aBareRgbStyleIsOpaqueRatherThanInvisible() {
        assertEquals(0xFFFFFFAA, new OutlineStyle(0xFFFFAA, 20).argb());
    }

    @Test
    void anExplicitAlphaIsKept() {
        assertEquals(0x40FFFFAA, new OutlineStyle(0x40FFFFAA, 20).argb());
    }

    @Test
    void revelioPaleYellowBecomesAVividYellowOfTheSameHue() {
        // S 0.33 -> 0.6 at the same hue and brightness: the blue channel drops, red and green stay full.
        assertEquals(0xFFFF66, OutlineStyle.vivid(0xFFFFAA));
    }

    @Test
    void anAlreadyVividColourIsLeftExactlyAlone() {
        for (int rgb : List.of(0xFF0000, 0x00FF00, 0x2255FF, 0xE0A020)) {
            assertEquals(rgb, OutlineStyle.vivid(rgb), () -> Integer.toHexString(rgb));
        }
    }

    @Test
    void aDarkColourIsBrightenedWithoutChangingItsHue() {
        // 0x400000 is a dark red: brightness lifts to 0.8 (0xCC), saturation is already full.
        assertEquals(0xCC0000, OutlineStyle.vivid(0x400000));
    }

    @Test
    void greyGetsBrighterButIsNotGivenAnInventedHue() {
        int vivid = OutlineStyle.vivid(0x404040);

        assertEquals(ARGB.red(vivid), ARGB.green(vivid));
        assertEquals(ARGB.green(vivid), ARGB.blue(vivid));
        assertTrue(ARGB.red(vivid) >= 0xCC);
    }

    @Test
    void aSpellStyleIsAlwaysOpaque() {
        assertEquals(0xFF, ARGB.alpha(OutlineStyle.forSpell(16777130, 20).argb()));
        assertEquals(0xFF, ARGB.alpha(OutlineStyle.forSpell(-86, 20).argb()));
    }

    // ── fade ──

    @Test
    void fullStrengthUntilTheLastSecond() {
        assertEquals(1.0f, OutlineEntry.fade(1000, 900, 0.0f));
        assertEquals(1.0f, OutlineEntry.fade(1000, 1000 - OutlineEntry.FADE_TICKS, 0.0f));
    }

    @Test
    void easesToNothingAtExpiryAndStaysThere() {
        assertEquals(0.5f, OutlineEntry.fade(1000, 990, 0.0f), 1.0e-6f);
        assertEquals(0.475f, OutlineEntry.fade(1000, 990, 0.5f), 1.0e-6f);
        assertEquals(0.0f, OutlineEntry.fade(1000, 1000, 0.0f));
        // The server's clear can arrive a tick late; until it does the outline must stay invisible, not return.
        assertEquals(0.0f, OutlineEntry.fade(1000, 1005, 0.0f));
    }

    @Test
    void aPermanentOutlineNeverFades() {
        assertEquals(1.0f, OutlineEntry.fade(OutlineEntry.NEVER, Long.MAX_VALUE - 1, 0.9f));
    }

    @Test
    void aFullyFadedColourIsTheClearNotATransparentColour() {
        // Non-zero RGB with alpha 0 would still read as "draw an outline" everywhere downstream.
        OutlineEntry entry = new OutlineEntry(0xFFFFFF55, 1000);
        assertEquals(0, entry.colourAt(1000, 0.0f));
        assertEquals(0, entry.colourAt(999, 0.99f));
    }

    @Test
    void halfwayThroughTheFadeKeepsTheColourAndHalvesTheAlpha() {
        int faded = new OutlineEntry(0xFFFFFF55, 1000).colourAt(990, 0.0f);
        assertEquals(0xFFFF55, faded & 0xFFFFFF);
        assertEquals(0x7F, ARGB.alpha(faded), 1);
    }
}
