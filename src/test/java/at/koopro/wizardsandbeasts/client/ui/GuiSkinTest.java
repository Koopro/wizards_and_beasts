package at.koopro.wizardsandbeasts.client.ui;

import at.koopro.wizardsandbeasts.client.gui.WizardsPalette.GuiSkin;
import at.koopro.wizardsandbeasts.client.gui.util.UiContrast;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The two things a screen assumes when it takes a skin.
 *
 * <p>This is a regression guard with a specific history. Four of the six materials are <em>light</em>,
 * and every colour in {@code WizardsPalette} above the skin table was picked against the dark leather
 * panel. The wand trial drew its labels in {@code BRASS_HI}, which is 1.35 : 1 on the workbench's
 * face; Gringotts drew Galleons in {@code #D4AF37}, 1.58 : 1 on the ledger's. Wiring a skin without
 * also taking its ink produces a screen that renders perfectly and cannot be read, which no compiler
 * and no screenshot-free test would have caught.
 */
class GuiSkinTest {

    /**
     * Every material's own ink is readable on its own face.
     *
     * <p>The one guarantee a screen is allowed to make without measuring: draw body text in
     * {@code skin.ink()} on {@code skin.base()} and it will clear AA.
     */
    @Test
    void inkIsReadableOnItsOwnBase() {
        for (GuiSkin skin : GuiSkin.values()) {
            double ratio = UiContrast.ratio(skin.ink(), skin.base());
            assertTrue(ratio >= UiContrast.AA_TEXT,
                    () -> skin + " ink " + hex(skin.ink()) + " on base " + hex(skin.base())
                            + " is " + String.format("%.2f", UiContrast.ratio(skin.ink(), skin.base()))
                            + " : 1, below the " + UiContrast.AA_TEXT + " AA target");
        }
    }

    /**
     * A material's panel edge is visible against its own face — by whichever of its two edge
     * colours that material actually uses.
     *
     * <p>Not "the accent is legible on the base", which is the obvious assertion and is false. The
     * generated art settles it: sampling {@code divider.png} for the four light skins, the rule is
     * carried entirely by its dark seat row at 9.4–11.9 : 1, and the accent row beside it sits at
     * 1.6–2.3 : 1. The accent is the <em>highlight on</em> the edge, not the edge.
     *
     * <p>So the skins split into two families and the invariant has to hold for both. Five are a
     * light face with a dark frame doing the work (5.9–10.5 : 1, accent 1.6–3.7). {@code STAR_CHART}
     * is the inverse — a night void whose indigo frame is deliberately near-invisible against it at
     * 1.3 : 1, with the brass accent carrying the edge at 5.7 : 1. Requiring either one alone would
     * reject a material that is correct; requiring the stronger of the two rejects only a material
     * with no edge at all.
     *
     * <p>{@code AA_LARGE} rather than {@code AA_TEXT} because an edge is furniture. It has to be
     * seen, not read.
     */
    @Test
    void everySkinHasALegibleEdge() {
        for (GuiSkin skin : GuiSkin.values()) {
            double viaFrame = UiContrast.ratio(skin.frame(), skin.base());
            double viaAccent = UiContrast.ratio(skin.accent(), skin.base());
            assertTrue(Math.max(viaFrame, viaAccent) >= UiContrast.AA_LARGE,
                    () -> skin + " has no legible edge on its own face " + hex(skin.base())
                            + ": frame " + hex(skin.frame()) + " is "
                            + String.format("%.2f", viaFrame) + " : 1 and accent "
                            + hex(skin.accent()) + " is " + String.format("%.2f", viaAccent)
                            + " : 1, neither reaching " + UiContrast.AA_LARGE);
        }
    }

    /**
     * {@code UiContrast.readableOn} rescues a semantic colour on any material.
     *
     * <p>Gringotts is the case: its three coin colours carry meaning and cannot simply be replaced
     * by the ledger's ink, so they go through {@code readableOn} instead. This asserts that route
     * actually works on every face, rather than that it works on the one the author tried.
     */
    @Test
    void semanticColoursCanBeRescuedOnAnyBase() {
        int[] coins = {0xFFD4AF37, 0xFFC0C0C0, 0xFFCD7F32};
        for (GuiSkin skin : GuiSkin.values()) {
            for (int coin : coins) {
                int fixed = UiContrast.readableOn(coin, skin.base(), UiContrast.AA_TEXT);
                assertTrue(UiContrast.ratio(fixed, skin.base()) >= UiContrast.AA_TEXT,
                        () -> "readableOn could not lift " + hex(coin) + " onto " + skin
                                + " base " + hex(skin.base()));
            }
        }
    }

    /** Two materials that look the same are one material with two names. */
    @Test
    void everySkinHasADistinctFaceAndFolder() {
        for (GuiSkin a : GuiSkin.values()) {
            for (GuiSkin b : GuiSkin.values()) {
                if (a.ordinal() >= b.ordinal()) {
                    continue;
                }
                assertNotEquals(a.folder(), b.folder(), a + " and " + b + " share a sprite folder");
                assertNotEquals(a.base(), b.base(), a + " and " + b + " share a face colour");
            }
        }
    }

    /**
     * Skins are opaque.
     *
     * <p>They are nine-slice art and panel fills, not overlays. A skin colour that arrived with a
     * zero alpha byte — the shape a hand-typed {@code 0x14172B} takes — would draw as nothing, and
     * the failure mode is an invisible panel rather than an error.
     */
    @Test
    void everySkinColourIsOpaque() {
        for (GuiSkin skin : GuiSkin.values()) {
            for (int colour : new int[]{skin.base(), skin.frame(), skin.ink(),
                    skin.accent(), skin.muted()}) {
                assertTrue((colour & 0xFF000000) == 0xFF000000,
                        () -> skin + " has a non-opaque colour " + hex(colour));
            }
        }
    }

    /**
     * The hearth's accent is the Floo network's own green.
     *
     * <p>{@code WizardsPalette} spells its values rather than importing them, so this tie lives in a
     * javadoc sentence and would rot silently. On the Floo screen that green is the one colour
     * carrying meaning rather than theme; a material built around a green that has drifted from it
     * is a material built around nothing.
     */
    @Test
    void hearthAccentIsFlooGreen() {
        assertEquals(at.koopro.wizardsandbeasts.floo.FlooCues.EMERALD, GuiSkin.HEARTH.accent(),
                "GuiSkin.HEARTH.accent() and FlooCues.EMERALD have drifted apart");
    }

    private static String hex(int argb) {
        return String.format("#%06X", argb & 0x00FFFFFF);
    }
}
