package at.koopro.wizardsandbeasts.client.beam;

import at.koopro.wizardsandbeasts.visual.beam.BeamVisual;
import at.koopro.wizardsandbeasts.visual.beam.BeamVisualDefaults;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The default beams must look exactly as they did before the Visuals section existed. The expected values are the
 * table {@code BeamAppearance} carried (copied here, glow curve included), compared as the renderer draws them: glow
 * shells capped at two (what {@code BeamGeometry} ever drew), sparks at 96/256, no fades.
 */
class BeamAppearanceDefaultsTest {

    private static final int RED = 0xFFC03030;
    private static final int GREEN = 0xFF00FF00;
    private static final int BLUE = 0xFF3070E0;

    /** The glow curve BeamAppearance shipped with, verbatim. */
    private static int oldGlowFor(int color) {
        float r = ((color >> 16) & 0xFF) / 255f;
        float g = ((color >> 8) & 0xFF) / 255f;
        float b = (color & 0xFF) / 255f;
        return ((int) (brighten(r) * 255f) << 16) | ((int) (brighten(g) * 255f) << 8) | (int) (brighten(b) * 255f);
    }

    private static float brighten(float channel) {
        final float amount = 0.32f;
        return Math.min(1f, channel + (1f - channel) * amount * channel + amount * 0.15f);
    }

    private static void assertDrawsLike(BeamStyle expected, BeamStyle actual) {
        assertEquals(Math.round(expected.width()), Math.round(actual.width()), "width");
        assertEquals(Math.round(expected.height()), Math.round(actual.height()), "height");
        assertEquals(expected.coreColor() & 0xFFFFFF, actual.coreColor() & 0xFFFFFF, "core colour");
        assertEquals(expected.glowColor() & 0xFFFFFF, actual.glowColor() & 0xFFFFFF, "glow colour");
        assertEquals(expected.coreOpacity(), actual.coreOpacity(), 1e-6, "core opacity");
        assertEquals(expected.glowOpacity(), actual.glowOpacity(), 1e-6, "glow opacity");
        assertEquals(Math.min(2, expected.bloomLayers()), Math.min(2, actual.bloomLayers()), "shells drawn");
        assertEquals(expected.spin(), actual.spin(), 1e-6, "spin");
        assertEquals(expected.additive(), actual.additive(), "blend");
        assertEquals(96, Math.round(actual.sparkDensity() * 256f), "spark share");
    }

    private static BeamAppearance.Appearance of(String spell, int color) {
        BeamVisual visual = BeamVisualDefaults.forSpell(spell, color);
        assertNotNull(visual, spell);
        BeamAppearance.Appearance look = BeamAppearance.of(visual);
        assertEquals(0, look.fadeInTicks());
        assertEquals(0, look.fadeOutTicks());
        return look;
    }

    @Test
    void crucioIsTheSameBolt() {
        BeamAppearance.Appearance look = of(BeamVisualDefaults.CRUCIO, RED);
        assertDrawsLike(BeamStyle.lightning(oldGlowFor(RED)), look.style());
        assertEquals(new Lightning(6, 4f, 2), look.shape());
    }

    @Test
    void avadaIsTheSameJet() {
        BeamAppearance.Appearance look = of(BeamVisualDefaults.AVADA, GREEN);
        assertDrawsLike(new BeamStyle(3.0f, 3.0f, 0xCCFFCC, GREEN, 0.9f, 0.6f, 4, 0f, true), look.style());
        assertInstanceOf(Laser.class, look.shape());
    }

    @Test
    void aguamentiIsTheSameLaser() {
        BeamAppearance.Appearance look = of(BeamVisualDefaults.AGUAMENTI, BLUE);
        assertDrawsLike(BeamStyle.laser(oldGlowFor(BLUE)), look.style());
        assertInstanceOf(Laser.class, look.shape());
    }

    @Test
    void leviosaStillDrawsNothingByDefault() {
        BeamVisual leviosa = BeamVisualDefaults.forSpell(BeamVisualDefaults.LEVIOSA, BLUE);
        assertNotNull(leviosa);
        assertFalse(leviosa.enabled());
    }

    @Test
    void namespacedAndBareIdsResolveAlike() {
        assertTrue(BeamVisualDefaults.isBeamSpell("wizards_and_beasts:crucio"));
        assertTrue(BeamVisualDefaults.isBeamSpell("crucio"));
        assertFalse(BeamVisualDefaults.isBeamSpell("stupefy"));
        assertFalse(BeamVisualDefaults.isBeamSpell("other_mod:crucio"));
    }

    @Test
    void opacityScaleDimsWithoutChangingShape() {
        BeamStyle full = BeamStyle.laser(0x44CCFF);
        BeamStyle half = full.withOpacityScale(0.5f);
        assertEquals(full.coreOpacity() * 0.5f, half.coreOpacity(), 1e-6);
        assertEquals(full.glowOpacity() * 0.5f, half.glowOpacity(), 1e-6);
        assertEquals(full.width(), half.width());
        assertTrue(full == full.withOpacityScale(1f), "a full-brightness scale allocates nothing");
    }
}
