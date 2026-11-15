package at.koopro.wizardsandbeasts.client.wand;

import at.koopro.wizardsandbeasts.Config;
import at.koopro.wizardsandbeasts.wand.visual.WandMetal;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** How often a wand at rest sheds its light: never when off, more when full, more for a stronger core. */
class WandIdleEffectsTest {

    @Test
    void offShowsNothing() {
        assertEquals(0, WandIdleEffects.moteInterval(Config.WandEffects.OFF, 1.0f));
        assertEquals(0, WandIdleEffects.glintInterval(Config.WandEffects.OFF, WandMetal.GOLD, true));
    }

    @Test
    void fullShedsMoreThanSubtleAndAStrongCoreMoreThanAWeakOne() {
        int subtle = WandIdleEffects.moteInterval(Config.WandEffects.SUBTLE, 0.85f);
        int full = WandIdleEffects.moteInterval(Config.WandEffects.FULL, 0.85f);
        int weak = WandIdleEffects.moteInterval(Config.WandEffects.SUBTLE, 0.25f);
        assertTrue(full < subtle && subtle < weak, full + " < " + subtle + " < " + weak);
    }

    @Test
    void onlyGiltOrTheElderWandGlints() {
        assertEquals(0, WandIdleEffects.glintInterval(Config.WandEffects.FULL, WandMetal.SILVER, false));
        assertTrue(WandIdleEffects.glintInterval(Config.WandEffects.SUBTLE, WandMetal.GOLD, false) > 0);
        assertTrue(WandIdleEffects.glintInterval(Config.WandEffects.SUBTLE, WandMetal.PEWTER, true) > 0);
    }
}
