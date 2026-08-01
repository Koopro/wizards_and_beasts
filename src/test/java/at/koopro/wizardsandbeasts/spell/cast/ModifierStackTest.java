package at.koopro.wizardsandbeasts.spell.cast;

import at.koopro.wizardsandbeasts.spell.core.SpellCategory;
import at.koopro.wizardsandbeasts.wand.cast.WandStats;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The cast-stack bounds every modifier source ultimately answers to. Wood contributions became
 * datapack-authored, which means a datapack can now push arbitrary multipliers into this stack —
 * so the clamp is what stops a bad definition from producing an unbounded cast.
 */
class ModifierStackTest {

    @Test
    void damageAndCooldown_clampToHardBounds() {
        ModifierStack high = new ModifierStack();
        high.multiplyDamage(50.0f, "test");
        high.multiplyCooldown(50.0f, "test");
        assertEquals(ModifierStack.HARD_CAP, high.finalDamage());
        assertEquals(ModifierStack.HARD_CAP, high.finalCooldown());

        ModifierStack low = new ModifierStack();
        low.multiplyDamage(0.001f, "test");
        low.multiplyCooldown(0.001f, "test");
        assertEquals(ModifierStack.HARD_FLOOR, low.finalDamage());
        assertEquals(ModifierStack.HARD_FLOOR, low.finalCooldown());
    }

    @Test
    void misfireChance_clampsToUnitRange() {
        ModifierStack over = new ModifierStack();
        over.addMisfireChance(5.0f, "test");
        assertEquals(1.0f, over.finalMisfireChance());

        ModifierStack under = new ModifierStack();
        under.addMisfireChance(-5.0f, "test");
        assertEquals(0.0f, under.finalMisfireChance());
    }

    /**
     * A datapack wood cannot escape the cap even at absurd values, and the clamp is applied at read
     * time so the order sources push in does not change the result.
     */
    @Test
    void runawayWoodContribution_stillRespectsTheCap() {
        WandStats absurd = WandStats.builder().mulDamage(1000.0f).mulCooldown(0.0f).build();

        ModifierStack stack = new ModifierStack();
        stack.multiplyDamage(absurd.damageMultiplier(), "wand");
        stack.multiplyCooldown(absurd.cooldownMultiplier(), "wand");

        assertEquals(ModifierStack.HARD_CAP, stack.finalDamage());
        assertEquals(ModifierStack.HARD_FLOOR, stack.finalCooldown());
    }

    @Test
    void multiplicationIsOrderIndependent() {
        ModifierStack a = new ModifierStack();
        a.multiplyDamage(1.05f, "wood");
        a.multiplyDamage(0.6f, "allegiance");

        ModifierStack b = new ModifierStack();
        b.multiplyDamage(0.6f, "allegiance");
        b.multiplyDamage(1.05f, "wood");

        assertEquals(a.finalDamage(), b.finalDamage(), 1e-6f,
                "Sources are multiplied into shared accumulators and clamped only on read.");
    }

    @Test
    void neutralWandStats_leaveTheStackUntouched() {
        WandStats neutral = new WandStats(1.0f, 1.0f, 1.0f, 0.0f, Map.<SpellCategory, Float>of());
        ModifierStack stack = new ModifierStack();
        stack.multiplyDamage(neutral.damageMultiplier(), "wand");
        stack.multiplyCooldown(neutral.cooldownMultiplier(), "wand");

        assertEquals(1.0f, stack.finalDamage(), 1e-6f);
        assertEquals(1.0f, stack.finalCooldown(), 1e-6f);
        assertTrue(stack.provenance().size() == 2, "Provenance must record every contributor.");
    }
}
