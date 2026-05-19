package at.koopro.wizardsandbeasts.brew;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Unit tests for the level-based brewing potency multiplier. Validates the
 * scaling table that wires {@link at.koopro.wizardsandbeasts.skill.HerbologySkills#POTION_POTENCY}
 * into the brewing pillar.
 */
class BrewPotencyTest {

    private static final float DELTA = 1.0e-6f;

    @Test
    void noLevels_returnsOne() {
        assertEquals(1.0f, BrewPotency.multiplierForLevel(0), DELTA,
                "Players without the skill must not have shortened potions.");
    }

    @Test
    void perLevel_addsTenPercent() {
        assertEquals(1.10f, BrewPotency.multiplierForLevel(1), DELTA);
        assertEquals(1.20f, BrewPotency.multiplierForLevel(2), DELTA);
        assertEquals(1.30f, BrewPotency.multiplierForLevel(3), DELTA);
    }

    @Test
    void aboveCap_clampedToMax() {
        float capped = BrewPotency.multiplierForLevel(BrewPotency.MAX_LEVEL_BONUS);
        assertEquals(capped, BrewPotency.multiplierForLevel(BrewPotency.MAX_LEVEL_BONUS + 1), DELTA);
        assertEquals(capped, BrewPotency.multiplierForLevel(99), DELTA,
                "Defensive cap protects against datapacks raising maxLevel beyond intent.");
    }

    @Test
    void negativeLevel_clampedToZero() {
        assertEquals(1.0f, BrewPotency.multiplierForLevel(-1), DELTA,
                "Negative inputs must never produce a sub-1.0 multiplier (would shorten potions).");
        assertEquals(1.0f, BrewPotency.multiplierForLevel(Integer.MIN_VALUE), DELTA);
    }
}
