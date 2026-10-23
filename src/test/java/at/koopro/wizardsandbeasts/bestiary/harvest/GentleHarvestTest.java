package at.koopro.wizardsandbeasts.bestiary.harvest;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Gentle Hands' extra yield and research's shorter wait, as numbers. */
class GentleHarvestTest {

    @Test
    void noGentlenessNoExtra() {
        assertEquals(0, GentleHarvest.extra(3, 0f, 0f));
        assertEquals(0, GentleHarvest.extra(0, 0.75f, 0f));
    }

    @Test
    void aQuarterPerLevelOfWhatWasTaken() {
        // One hair at one level: a one-in-four chance of a second.
        assertEquals(1, GentleHarvest.extra(1, 0.25f, 0.10f));
        assertEquals(0, GentleHarvest.extra(1, 0.25f, 0.30f));
        // Four hairs at one level: exactly one more, no roll needed.
        assertEquals(1, GentleHarvest.extra(4, 0.25f, 0.99f));
        // Two hairs at three levels: 1.5 -> one sure, a coin flip for the second.
        assertEquals(2, GentleHarvest.extra(2, 0.75f, 0.40f));
        assertEquals(1, GentleHarvest.extra(2, 0.75f, 0.60f));
    }

    @Test
    void researchHalvesTheWait() {
        assertEquals(24000, GentleHarvest.cooldown(24000, false));
        assertEquals(12000, GentleHarvest.cooldown(24000, true));
    }
}
