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

    /**
     * The bounds moved from this class to {@link SpellPower}, and two of them changed meaning.
     *
     * <p>Damage is now soft-capped rather than truncated: it approaches the ceiling asymptotically,
     * so an absurd input still lands on it but a merely-large one does not. Cooldown gained its own
     * pair of bounds — a floor of 0.25 as the anti-spam guard and a ceiling of 2.0, rather than the
     * damage ceiling of 3.0 it used to borrow, because a penalty stack that triples a cooldown reads
     * as a spell being taken away.
     */
    @Test
    void damageAndCooldown_clampToHardBounds() {
        SpellPower.Bounds bounds = SpellPower.configuredBounds();

        ModifierStack high = new ModifierStack();
        high.multiplyDamage(50.0f, "test");
        high.multiplyCooldown(50.0f, "test");
        assertEquals(bounds.max(), high.finalDamage(), 1e-4f);
        assertEquals(bounds.cooldownMax(), high.finalCooldown(), 1e-4f);

        ModifierStack low = new ModifierStack();
        low.multiplyDamage(0.001f, "test");
        low.multiplyCooldown(0.001f, "test");
        assertEquals(bounds.min(), low.finalDamage(), 1e-4f);
        assertEquals(bounds.cooldownMin(), low.finalCooldown(), 1e-4f);
    }

    /**
     * The named channels are set, never accumulated, so proficiency cannot be applied twice.
     *
     * <p>This is the invariant that failed before {@link SpellPower} existed: {@code SkillSystemAPI}
     * pushed the proficiency tier into the situational bag and the cast site multiplied the
     * proficiency curve on top of it.
     */
    @Test
    void namedChannelsAreSetNotAccumulated() {
        ModifierStack stack = new ModifierStack();
        stack.setProficiency(1.2f, 0.9f);
        stack.setProficiency(1.2f, 0.9f);
        assertEquals(1.2f, stack.damageBreakdown().proficiency(), 1e-6f,
                "a second set overwrites; it must not compound to 1.44");
        assertEquals(0.9f, stack.cooldownBreakdown().proficiency(), 1e-6f);
    }

    @Test
    void theThreeChannelsAreReportedSeparately() {
        ModifierStack stack = new ModifierStack();
        stack.multiplyDamage(1.1f, "wand");
        stack.setProficiency(1.2f, 1.0f);
        stack.setSkill(1.05f, 1.0f);

        SpellPower.Breakdown power = stack.damageBreakdown();
        assertEquals(1.1f, power.situational(), 1e-6f);
        assertEquals(1.2f, power.proficiency(), 1e-6f);
        assertEquals(1.05f, power.skill(), 1e-6f);
        assertEquals(1.1f * 1.2f * 1.05f, power.total(), 1e-4f);
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

        assertEquals(SpellPower.configuredBounds().max(), stack.finalDamage(), 1e-4f);
        assertEquals(SpellPower.configuredBounds().cooldownMin(), stack.finalCooldown(), 1e-4f);
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
