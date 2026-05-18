package at.koopro.wizardsandbeasts.wand.cast;

import at.koopro.wizardsandbeasts.spell.core.SpellCategory;
import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies invariants of {@link WandStats} that the cast pipeline relies on:
 * fizzle clamping, defensive map copy, and additive category bonuses.
 *
 * <p>{@code damageFor / cooldownFor / rangeFor} are not exercised here because
 * they require constructed {@link at.koopro.wizardsandbeasts.spell.Spell} instances; those
 * are exercised end-to-end at runtime.
 */
class WandStatsTest {

    @Test
    void neutral_defaultsAreIdentities() {
        WandStats neutral = WandStats.NEUTRAL;
        assertEquals(1.0f, neutral.damageMultiplier());
        assertEquals(1.0f, neutral.cooldownMultiplier());
        assertEquals(1.0f, neutral.rangeMultiplier());
        assertEquals(0.0f, neutral.fizzleChance());
        assertTrue(neutral.categoryDamageBonus().isEmpty());
    }

    @Test
    void fizzleChance_isClampedTo0to1() {
        WandStats high = new WandStats(1.0f, 1.0f, 1.0f, 5.0f, Map.of());
        assertEquals(1.0f, high.fizzleChance(), "fizzle > 1 must clamp to 1");

        WandStats low = new WandStats(1.0f, 1.0f, 1.0f, -0.5f, Map.of());
        assertEquals(0.0f, low.fizzleChance(), "fizzle < 0 must clamp to 0");
    }

    @Test
    void categoryDamageBonus_isUnmodifiableCopy() {
        EnumMap<SpellCategory, Float> mutable = new EnumMap<>(SpellCategory.class);
        mutable.put(SpellCategory.COMBAT, 0.10f);

        WandStats stats = new WandStats(1.0f, 1.0f, 1.0f, 0.0f, mutable);

        mutable.put(SpellCategory.DARK_ARTS, 0.99f);
        assertEquals(1, stats.categoryDamageBonus().size(),
                "WandStats must defensively copy the bonus map.");

        assertThrows(UnsupportedOperationException.class,
                () -> stats.categoryDamageBonus().put(SpellCategory.DEFENSE, 1.0f),
                "categoryDamageBonus() must return an unmodifiable view.");
    }

    @Test
    void builder_combinesContributorsMultiplicativelyAndAdditively() {
        WandStats combined = WandStats.builder()
                .mulDamage(1.10f)
                .mulDamage(1.05f)               // contributors compose multiplicatively
                .mulCooldown(0.95f)
                .addCategoryDamageBonus(SpellCategory.COMBAT, 0.10f)
                .addCategoryDamageBonus(SpellCategory.COMBAT, 0.05f) // category bonuses sum
                .addFizzle(0.03f)
                .addFizzle(-0.02f)              // fizzle deltas sum
                .build();

        assertEquals(1.10f * 1.05f, combined.damageMultiplier(), 1e-5f);
        assertEquals(0.95f, combined.cooldownMultiplier(), 1e-5f);
        assertEquals(0.15f, combined.categoryDamageBonus().get(SpellCategory.COMBAT), 1e-5f);
        assertEquals(0.01f, combined.fizzleChance(), 1e-5f);
    }

    @Test
    void resolver_nullStack_returnsNeutral() {
        assertEquals(WandStats.NEUTRAL, WandStatsResolver.resolve(null));
    }
}
