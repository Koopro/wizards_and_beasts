package at.koopro.wizardsandbeasts.client.ui;

import at.koopro.wizardsandbeasts.client.gui.WizardsPalette.GuiSkin;
import at.koopro.wizardsandbeasts.client.gui.ui.UiSurface;
import at.koopro.wizardsandbeasts.client.gui.util.UiContrast;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Every surface's inks read on its own field.
 *
 * <p>A component takes a {@link UiSurface} instead of colours precisely so that it cannot pick a light
 * colour for light paper. That only holds if each surface's inks are themselves legible, which is what
 * this pins: body ink and label ink at AA for text, captions, meaning colours (good, bad, warn) and the
 * disabled ink at AA-large. Disabled text has to be seen, not read twice; a caption is never the only
 * place something a player must act on is said.
 */
class UiSurfaceTest {

    private static List<UiSurface> surfaces() {
        List<UiSurface> all = new ArrayList<>(List.of(UiSurface.PAGE, UiSurface.NIGHT, UiSurface.NIGHT_SHEET));
        for (GuiSkin skin : GuiSkin.values()) {
            all.add(UiSurface.of(skin));
        }
        return all;
    }

    private static String name(UiSurface s) {
        return s == UiSurface.PAGE ? "PAGE" : s == UiSurface.NIGHT ? "NIGHT"
                : s == UiSurface.NIGHT_SHEET ? "NIGHT_SHEET" : String.valueOf(s.skin());
    }

    private static void atLeast(UiSurface s, String role, int ink, double floor) {
        // Translucent fields (the night card) are judged as if laid over black, their worst case in play.
        int field = s.field() | 0xFF000000;
        double ratio = UiContrast.ratio(ink, field);
        assertTrue(ratio >= floor, () -> String.format(Locale.ROOT, "%s %s %08X on %08X is %.2f : 1, below %.1f",
                name(s), role, ink, field, ratio, floor));
    }

    @Test
    void bodyAndLabelInkAreReadable() {
        for (UiSurface s : surfaces()) {
            atLeast(s, "ink", s.ink(), UiContrast.AA_TEXT);
            atLeast(s, "ink2", s.ink2(), UiContrast.AA_TEXT);
            atLeast(s, "heading", s.heading(), UiContrast.AA_TEXT);
        }
    }

    @Test
    void secondaryInksCanBeSeen() {
        for (UiSurface s : surfaces()) {
            atLeast(s, "ink3", s.ink3(), UiContrast.AA_LARGE);
            atLeast(s, "good", s.good(), UiContrast.AA_LARGE);
            atLeast(s, "bad", s.bad(), UiContrast.AA_LARGE);
            atLeast(s, "warn", s.warn(), UiContrast.AA_LARGE);
            atLeast(s, "disabled", s.disabled(), UiContrast.AA_LARGE);
        }
    }

    /**
     * The night sheet's text mostly sits in its wells (the spell list, the details card) and on its
     * cloth, whose posterised grain has a lighter tone than the field ({@code gui_parchment.py}
     * {@code field_l}). Its inks must hold on both, not only on the field the record names.
     */
    @Test
    void nightSheetInksHoldOnItsWellAndItsLightestCloth() {
        UiSurface s = UiSurface.NIGHT_SHEET;
        for (int ground : new int[]{s.well(), 0xFF241E2C}) {
            UiSurface on = new UiSurface(ground, s.well(), s.edge(), s.ink(), s.ink2(), s.ink3(), s.heading(),
                    s.good(), s.bad(), s.warn(), s.accent(), s.disabled(), s.selectWash(), s.hoverWash(),
                    s.shadow(), null);
            atLeast(on, "ink", on.ink(), UiContrast.AA_TEXT);
            atLeast(on, "ink2", on.ink2(), UiContrast.AA_TEXT);
            atLeast(on, "heading", on.heading(), UiContrast.AA_TEXT);
            atLeast(on, "ink3", on.ink3(), UiContrast.AA_LARGE);
            atLeast(on, "warn", on.warn(), UiContrast.AA_LARGE);
            atLeast(on, "disabled", on.disabled(), UiContrast.AA_LARGE);
        }
    }

    @Test
    void textShadowBelongsToDarkSurfacesOnly() {
        for (UiSurface s : surfaces()) {
            assertTrue(s.shadow() == s.dark(), () -> name(s) + ": shadow must match darkness");
        }
    }
}
