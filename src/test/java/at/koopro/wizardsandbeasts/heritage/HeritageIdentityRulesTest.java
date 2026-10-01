package at.koopro.wizardsandbeasts.heritage;

import at.koopro.wizardsandbeasts.heritage.veela.VeelaAllure;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The heritage identity rules added on top of the trait catalogue, pinned without a world. */
class HeritageIdentityRulesTest {

    @Test
    void allureReachesAndHoldsByLineage() {
        VeelaAllure.Strength full = VeelaAllure.strength(HeritageVariant.VEELA_FULL);
        VeelaAllure.Strength half = VeelaAllure.strength(HeritageVariant.VEELA_HALF);
        VeelaAllure.Strength quarter = VeelaAllure.strength(HeritageVariant.VEELA_QUARTER);
        assertNotNull(full);
        assertNotNull(half);
        assertNotNull(quarter);
        assertTrue(full.radius() > half.radius() && half.radius() > quarter.radius(), "reach falls with the blood");
        assertTrue(full.durationTicks() > half.durationTicks() && half.durationTicks() > quarter.durationTicks(),
                "hold falls with the blood");
        assertNull(VeelaAllure.strength(HeritageVariant.HALF_BLOOD), "a wizard has no allure");
        assertNull(VeelaAllure.strength(null));
    }

    @Test
    void aStrongWillShakesOffTheAllureMoreOften() {
        // resistScalar runs 0.4 (no will) to 1.0 (full will); the chance to resist is half of it.
        assertTrue(VeelaAllure.resists(0.19f, 0.4f));
        assertFalse(VeelaAllure.resists(0.21f, 0.4f), "no will: a one-in-five chance, no better");
        assertTrue(VeelaAllure.resists(0.49f, 1.0f));
        assertFalse(VeelaAllure.resists(0.5f, 1.0f), "even a perfect will does not make anyone immune");
    }

    @Test
    void everyCastingLineageOfWizardkindCarriesWandworkAndTheLearnedArts() {
        for (HeritageVariant variant : Heritage.WIZARDKIND.getSubtypes()) {
            boolean casts = !variant.hasTag("no_casting");
            assertTrue(variant.hasTag("wandwork") == casts, variant + ": wandwork follows the ability to cast");
            assertTrue(variant.hasTag("learned_arts") == casts, variant + ": the learned arts follow casting");
        }
        assertFalse(HeritageVariant.VEELA_FULL.hasTag("wandwork"), "Wizardkind's breadth is its own");
        assertNotNull(HeritageTraits.byId("wandwork"));
        assertNotNull(HeritageTraits.byId("learned_arts"));
    }
}
