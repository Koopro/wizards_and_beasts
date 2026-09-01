package at.koopro.wizardsandbeasts.bestiary.harvest;

import at.koopro.wizardsandbeasts.bestiary.DiscoveryTier;
import net.minecraft.SharedConstants;
import net.minecraft.resources.Identifier;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The gating a player actually experiences: who gets a rare drop, how often, and how soon again.
 *
 * <p>{@link HarvestGate} is pure — no player, no loot context, no world — so all of it is pinned here
 * without a running game. The rules carry a placeholder item because none of these assertions touch it:
 * what the stack actually is only matters inside the loot modifier, which is the one seam a unit test
 * cannot reach.
 */
class HarvestGateTest {

    private static final long TICKS_PER_SECOND = 20L;

    private static final Identifier UNICORN =
            Identifier.fromNamespaceAndPath("wizards_and_beasts", "unicorn");

    @BeforeAll
    static void bootstrapMinecraft() {
        // HarvestRule holds an Item, and Items.AIR is only populated once the registries are built.
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    private static Item anyItem() {
        return Items.AIR;
    }

    /** A rule carrying a placeholder item, which is all the gate needs to decide. */
    private static HarvestRule rule(DiscoveryTier minTier, float chance, int cooldownSeconds) {
        return new HarvestRule(UNICORN, anyItem(), minTier, chance, 1, 1, cooldownSeconds);
    }

    // ── the tier gate: the point of the feature ──

    @Test
    void aRareDropNeedsTheDeclaredTier() {
        HarvestRule mastered = rule(DiscoveryTier.MASTERED, 1.0f, 0);

        assertFalse(mastered.tierSatisfiedBy(DiscoveryTier.UNDISCOVERED));
        assertFalse(mastered.tierSatisfiedBy(DiscoveryTier.SIGHTED));
        assertFalse(mastered.tierSatisfiedBy(DiscoveryTier.ENCOUNTERED));
        assertFalse(mastered.tierSatisfiedBy(DiscoveryTier.STUDIED));
        assertTrue(mastered.tierSatisfiedBy(DiscoveryTier.MASTERED));
    }

    @Test
    void aLowerFloorAdmitsEveryTierAboveIt() {
        HarvestRule studied = rule(DiscoveryTier.STUDIED, 1.0f, 0);

        assertFalse(studied.tierSatisfiedBy(DiscoveryTier.ENCOUNTERED));
        assertTrue(studied.tierSatisfiedBy(DiscoveryTier.STUDIED));
        assertTrue(studied.tierSatisfiedBy(DiscoveryTier.MASTERED),
                "mastering a creature must never take away what studying it gave");
    }

    @Test
    void anUnstudiedKillerIsIneligibleWhateverElseIsTrue() {
        HarvestRule r = rule(DiscoveryTier.MASTERED, 1.0f, 0);
        assertFalse(HarvestGate.isEligible(r, DiscoveryTier.UNDISCOVERED, HarvestGate.NEVER, 1_000L));
        assertFalse(HarvestGate.yields(r, DiscoveryTier.SIGHTED, HarvestGate.NEVER, 1_000L, 0.0f),
                "a roll of zero still cannot beat an unmet tier");
    }

    @Test
    void aStudiedKillerWithACertainRuleAlwaysYields() {
        HarvestRule r = rule(DiscoveryTier.MASTERED, 1.0f, 0);
        assertTrue(HarvestGate.yields(r, DiscoveryTier.MASTERED, HarvestGate.NEVER, 1_000L, 0.999f));
    }

    // ── chance ──

    @Test
    void theChanceRollIsExclusiveAtTheTopSoAOneInThreeRuleIsNotAOneInTwo() {
        HarvestRule third = rule(DiscoveryTier.STUDIED, 0.33f, 0);

        assertTrue(HarvestGate.yields(third, DiscoveryTier.STUDIED, HarvestGate.NEVER, 0L, 0.0f));
        assertTrue(HarvestGate.yields(third, DiscoveryTier.STUDIED, HarvestGate.NEVER, 0L, 0.329f));
        assertFalse(HarvestGate.yields(third, DiscoveryTier.STUDIED, HarvestGate.NEVER, 0L, 0.33f),
                "a roll equal to the chance must fail, or every rule is fractionally too generous");
        assertFalse(HarvestGate.yields(third, DiscoveryTier.STUDIED, HarvestGate.NEVER, 0L, 0.9f));
    }

    // ── the cooldown: the anti-farm gate ──

    @Test
    void aRuleWithNoCooldownNeverLocksOut() {
        HarvestRule r = rule(DiscoveryTier.STUDIED, 1.0f, 0);
        assertTrue(HarvestGate.isOffCooldown(r, 0L, 0L));
        assertTrue(HarvestGate.isOffCooldown(r, 5_000L, 5_001L));
    }

    @Test
    void aFirstEverHarvestIsNeverOnCooldown() {
        HarvestRule r = rule(DiscoveryTier.STUDIED, 1.0f, 600);
        assertTrue(HarvestGate.isOffCooldown(r, HarvestGate.NEVER, 0L));
        assertTrue(HarvestGate.isEligible(r, DiscoveryTier.STUDIED, HarvestGate.NEVER, 0L));
    }

    @Test
    void repeatedKillsInsideTheWindowYieldNothingFurther() {
        HarvestRule r = rule(DiscoveryTier.MASTERED, 1.0f, 600);
        long harvestedAt = 100_000L;

        assertFalse(HarvestGate.isEligible(r, DiscoveryTier.MASTERED, harvestedAt, harvestedAt),
                "the kill that just paid out cannot pay again");
        assertFalse(HarvestGate.isEligible(r, DiscoveryTier.MASTERED, harvestedAt, harvestedAt + 1L));
        assertFalse(HarvestGate.isEligible(r, DiscoveryTier.MASTERED, harvestedAt,
                harvestedAt + 600L * TICKS_PER_SECOND - 1L), "one tick short is still locked");
    }

    @Test
    void theWindowOpensExactlyOnTheDeclaredSecond() {
        HarvestRule r = rule(DiscoveryTier.MASTERED, 1.0f, 600);
        long harvestedAt = 100_000L;
        assertTrue(HarvestGate.isEligible(r, DiscoveryTier.MASTERED, harvestedAt,
                harvestedAt + 600L * TICKS_PER_SECOND),
                "600s must mean 600s, not 600s plus a tick");
    }

    /**
     * Game time is per-level, so stepping into the Nether can move it backwards by a large margin. A
     * naive {@code now - last >= cooldown} would then lock the player out for the rest of the world's
     * life — a farm-prevention measure that becomes a permanent ban by accident.
     */
    @Test
    void aClockThatWentBackwardsDoesNotLockAPlayerOutForever() {
        HarvestRule r = rule(DiscoveryTier.MASTERED, 1.0f, 600);
        assertTrue(HarvestGate.isOffCooldown(r, 5_000_000L, 12L),
                "a dimension change must not strand the lockout");
    }

    @Test
    void cooldownIsCheckedBeforeTheChanceRollSoAFarmCannotBurnAttempts() {
        HarvestRule r = rule(DiscoveryTier.MASTERED, 1.0f, 600);
        long harvestedAt = 100_000L;
        // A guaranteed rule with a roll of 0 — the most generous input there is — still yields nothing
        // while locked, which is only true if the cooldown is evaluated independently of the roll.
        assertFalse(HarvestGate.yields(r, DiscoveryTier.MASTERED, harvestedAt, harvestedAt + 10L, 0.0f));
    }

    // ── counts ──

    @Test
    void aFixedCountIgnoresTheRoll() {
        HarvestRule r = new HarvestRule(UNICORN, anyItem(), DiscoveryTier.STUDIED, 1.0f, 2, 2, 0);
        assertEquals(2, HarvestGate.countFor(r, 0.0f));
        assertEquals(2, HarvestGate.countFor(r, 0.99f));
    }

    @Test
    void aCountRangeSpansItsWholeInclusiveRange() {
        HarvestRule r = new HarvestRule(UNICORN, anyItem(), DiscoveryTier.STUDIED, 1.0f, 1, 3, 0);
        assertEquals(1, HarvestGate.countFor(r, 0.0f));
        assertEquals(2, HarvestGate.countFor(r, 0.5f));
        assertEquals(3, HarvestGate.countFor(r, 0.99f));
        assertEquals(3, HarvestGate.countFor(r, 1.0f),
                "a roll of exactly 1.0 must clamp rather than index past the top");
    }

    @Test
    void anInvertedCountRangeIsRepairedRatherThanTrusted() {
        HarvestRule r = new HarvestRule(UNICORN, anyItem(), DiscoveryTier.STUDIED, 1.0f, 5, 2, 0);
        assertEquals(5, r.minCount());
        assertEquals(5, r.maxCount());
        assertEquals(5, HarvestGate.countFor(r, 0.5f));
    }

    @Test
    void aZeroOrNegativeCountFloorsAtOneSoARuleNeverDropsAnEmptyStack() {
        HarvestRule r = new HarvestRule(UNICORN, anyItem(), DiscoveryTier.STUDIED, 1.0f, 0, 0, 0);
        assertEquals(1, r.minCount());
        assertEquals(1, HarvestGate.countFor(r, 0.5f));
    }

    @Test
    void aNegativeCooldownIsFlooredRatherThanInverted() {
        assertEquals(0, rule(DiscoveryTier.STUDIED, 1.0f, -600).cooldownSeconds());
    }

    // ── multiplayer isolation ──

    /**
     * Two players killing the same creature are two independent decisions. The gate takes the tier and
     * the last-harvest tick as arguments and holds no state of its own, so there is nothing for one
     * player's harvest to write that another player's could read.
     */
    @Test
    void twoPlayersAtDifferentTiersGetDifferentAnswersForTheSameKill() {
        HarvestRule r = rule(DiscoveryTier.MASTERED, 1.0f, 600);
        long now = 50_000L;

        boolean expert = HarvestGate.yields(r, DiscoveryTier.MASTERED, HarvestGate.NEVER, now, 0.0f);
        boolean novice = HarvestGate.yields(r, DiscoveryTier.SIGHTED, HarvestGate.NEVER, now, 0.0f);

        assertTrue(expert);
        assertFalse(novice);
    }

    @Test
    void oneCreaturesLockoutSaysNothingAboutAnother() {
        HarvestRule r = rule(DiscoveryTier.MASTERED, 1.0f, 600);
        long harvestedAt = 100_000L;

        // Same rule, two different last-harvest values — which is exactly how the caller keys the map
        // per bestiary entry. Mastering a unicorn must not throttle a phoenix.
        assertFalse(HarvestGate.isEligible(r, DiscoveryTier.MASTERED, harvestedAt, harvestedAt + 10L));
        assertTrue(HarvestGate.isEligible(r, DiscoveryTier.MASTERED, HarvestGate.NEVER, harvestedAt + 10L));
    }
}
