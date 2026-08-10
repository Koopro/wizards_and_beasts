package at.koopro.wizardsandbeasts.heritage;

import at.koopro.wizardsandbeasts.client.gui.WizardsPalette;
import at.koopro.wizardsandbeasts.client.gui.util.UiContrast;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guards that every heritage and lineage name stays legible where the selection screen prints it.
 *
 * <p>Signature colours are picked to work as a sigil — a disc, a bar, an outline — and several of
 * them fail as glyph colour on the dossier's leather field: Vampire ({@code 0x330055}) and Obscurial
 * ({@code 0x553366}) sink into it, Veela ({@code 0xFFCCEE}) glares off it. The screen runs them
 * through {@link UiContrast#readableOn} for exactly that reason, and this pins the result so a new
 * heritage colour cannot quietly ship unreadable.
 *
 * <p>The first assertion is the interesting one: it proves the lift is actually needed, so this test
 * fails loudly if someone deletes the {@code readableOn} call because "the colours look fine".
 */
class HeritageNameContrastTest {

    /** The dossier panel's field colour — what {@code McStylePanel.drawThemedPanel} resolves to. */
    private static final int GROUND = WizardsPalette.PLATE;

    @Test
    void atLeastOneHeritageColourNeedsTheLift() {
        boolean anyRawFailure = false;
        for (Heritage heritage : Heritage.values()) {
            if (UiContrast.ratio(heritage.getColor() | 0xFF000000, GROUND) < UiContrast.AA_LARGE) {
                anyRawFailure = true;
                break;
            }
        }
        assertTrue(anyRawFailure,
                "no heritage colour fails raw contrast any more — if the palette really did change, "
                        + "update this test; if not, the contrast lift is being removed on a false premise");
    }

    @Test
    void everyHeritageNameClearsLargeTextContrastAfterTheLift() {
        for (Heritage heritage : Heritage.values()) {
            int lifted = UiContrast.readableOn(heritage.getColor(), GROUND, UiContrast.AA_LARGE);
            double ratio = UiContrast.ratio(lifted, GROUND);
            assertTrue(ratio >= UiContrast.AA_LARGE,
                    heritage.getId() + " name renders at " + String.format("%.2f", ratio)
                            + ":1 on the dossier panel — below the " + UiContrast.AA_LARGE + ":1 floor");
        }
    }

    @Test
    void everyLineageColourClearsContrastToo() {
        // Lineage tints are not drawn as ink today, but they are the same kind of value and the
        // screen is one design change away from using them. Cheap to hold to the same floor.
        for (Heritage heritage : Heritage.values()) {
            for (HeritageVariant variant : heritage.getSubtypes()) {
                int lifted = UiContrast.readableOn(variant.getUiColor(), GROUND, UiContrast.AA_LARGE);
                assertTrue(UiContrast.ratio(lifted, GROUND) >= UiContrast.AA_LARGE,
                        variant.getId() + " lineage colour cannot be lifted to a readable value");
            }
        }
    }
}
