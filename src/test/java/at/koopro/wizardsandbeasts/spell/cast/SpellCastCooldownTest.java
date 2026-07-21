package at.koopro.wizardsandbeasts.spell.cast;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the cooldown-resolution invariant extracted from {@code SpellCastService}: a cast's cooldown is
 * the modifier-scaled value but never below 50% of the spell's base cooldown (PIPELINE_AUDIT.md §5),
 * with both terms clamped to at least one tick.
 */
class SpellCastCooldownTest {

    @Test
    void flooredAtHalfBase_whenReductionIsAggressive() {
        // base 100, mult 0.1 -> scaled 10, floor 50 -> the floor wins.
        assertEquals(50, SpellCastGate.resolveCooldownTicks(100, 0.1f));
    }

    @Test
    void identity_atMultOne() {
        assertEquals(100, SpellCastGate.resolveCooldownTicks(100, 1.0f));
    }

    @Test
    void noUpperCap_multGreaterThanOneLengthensCooldown() {
        assertEquals(200, SpellCastGate.resolveCooldownTicks(100, 2.0f));
    }

    @Test
    void neverBelowOneTick() {
        // base 1, mult 0 -> scaled max(1,0)=1, floor round(0.5)=1.
        assertEquals(1, SpellCastGate.resolveCooldownTicks(1, 0.0f));
        assertTrue(SpellCastGate.resolveCooldownTicks(0, 0.0f) >= 1);
    }

    @Test
    void floorApplies_betweenHalfAndFull() {
        // base 5, mult 0.3 -> scaled round(1.5)=2, floor round(2.5)=3 -> floor wins.
        assertEquals(3, SpellCastGate.resolveCooldownTicks(5, 0.3f));
    }
}
