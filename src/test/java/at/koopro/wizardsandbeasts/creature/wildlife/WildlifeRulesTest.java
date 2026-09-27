package at.koopro.wizardsandbeasts.creature.wildlife;

import at.koopro.wizardsandbeasts.bestiary.DiscoveryTier;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The rules that make creatures behave like wildlife, pinned without a world. */
class WildlifeRulesTest {

    // ── being watched ──

    @Test
    void aCreatureKnowsWhenItIsLookedStraightAt() {
        Vec3 ahead = new Vec3(0, 0, 10);
        assertTrue(WildlifeRules.looksAt(new Vec3(0, 0, 1), ahead));
        assertFalse(WildlifeRules.looksAt(new Vec3(1, 0, 0), ahead), "a creature at the edge of vision is unwatched");
        assertFalse(WildlifeRules.looksAt(new Vec3(0, 0, -1), ahead));
    }

    // ── the Demiguise ──

    @Test
    void aDemiguiseForeseesAStraightApproachOnly() {
        Vec3 toCreature = new Vec3(0, 0, 6);
        assertTrue(WildlifeRules.foresees(new Vec3(0, 0, 0.2), toCreature), "walking straight at it is predictable");
        assertFalse(WildlifeRules.foresees(new Vec3(0.2, 0, 0), toCreature), "coming at it sideways is not");
        assertFalse(WildlifeRules.foresees(new Vec3(0, 0, 0.01), toCreature), "standing about is not approaching");
        assertFalse(WildlifeRules.foresees(new Vec3(0, 0, -0.2), toCreature), "walking away is not approaching");
        assertFalse(WildlifeRules.foresees(new Vec3(0, 0.5, 0), toCreature), "a jump in place is not an approach");
    }

    @Test
    void aForeseenDemiguiseStepsOutOfThePathNotBackAlongIt() {
        Vec3 toCreature = new Vec3(0, 0, 6);
        Vec3 left = WildlifeRules.sidestep(toCreature, 4.0, 1);
        Vec3 right = WildlifeRules.sidestep(toCreature, 4.0, -1);
        assertTrue(Math.abs(left.x) >= 3.9 && Math.abs(right.x) >= 3.9, "the step is mostly sideways");
        assertTrue(left.x * right.x < 0, "the two sides are opposite");
        assertEquals(0.0, left.y, 1.0e-9);
    }

    // ── the Unicorn ──

    @Test
    void aUnicornLetsNearOnlyAQuietEmptyHandedWatcherWithAClearConscience() {
        assertTrue(WildlifeRules.letsNear(DiscoveryTier.OBSERVED, true, true, true, false, 0f));
        assertFalse(WildlifeRules.letsNear(DiscoveryTier.ENCOUNTERED, true, true, true, false, 0f),
                "a stranger it has not watched");
        assertFalse(WildlifeRules.letsNear(DiscoveryTier.KNOWN, false, true, true, false, 0f), "walking upright");
        assertFalse(WildlifeRules.letsNear(DiscoveryTier.KNOWN, true, false, true, false, 0f), "holding something");
        assertFalse(WildlifeRules.letsNear(DiscoveryTier.KNOWN, true, true, true, true, 0f), "a unicorn slayer");
        assertFalse(WildlifeRules.letsNear(DiscoveryTier.KNOWN, true, true, true, false, WildlifeRules.PURITY_LIMIT),
                "a darkened soul");
        assertTrue(WildlifeRules.letsNear(DiscoveryTier.KNOWN, true, true, false, true, 90f),
                "a creature that does not sense purity does not care");
    }

    @Test
    void aUnicornShunsSlayersAndTheDeeplyCorrupted() {
        assertTrue(WildlifeRules.shuns(true, 0f));
        assertTrue(WildlifeRules.shuns(false, WildlifeRules.TAINT));
        assertFalse(WildlifeRules.shuns(false, WildlifeRules.TAINT - 1f));
        assertTrue(WildlifeRules.PURITY_LIMIT < WildlifeRules.TAINT,
                "it refuses the touch of a lightly darkened soul before it flees one");
    }

    // ── the Phoenix ──

    @Test
    void aRebornPhoenixGrowsBackToFullSize() {
        assertEquals(WildlifeRules.REBORN_SCALE, WildlifeRules.rebirthScale(0), 1.0e-6f);
        float half = WildlifeRules.rebirthScale(WildlifeRules.REBIRTH_GROWTH_TICKS / 2);
        assertTrue(half > WildlifeRules.REBORN_SCALE && half < 1.0f);
        assertEquals(1.0f, WildlifeRules.rebirthScale(WildlifeRules.REBIRTH_GROWTH_TICKS), 1.0e-6f);
        assertEquals(1.0f, WildlifeRules.rebirthScale(Long.MAX_VALUE), 1.0e-6f);
    }

    // ── the Hippogriff ──

    @Test
    void aBowIsCrouchingStillWhileHoldingItsGaze() {
        assertTrue(WildlifeRules.bowing(true, 0.97, 0.0, 4.0));
        assertFalse(WildlifeRules.bowing(false, 0.97, 0.0, 4.0), "standing is not bowing");
        assertFalse(WildlifeRules.bowing(true, 0.5, 0.0, 4.0), "looking away breaks eye contact");
        assertFalse(WildlifeRules.bowing(true, 0.97, 0.05, 4.0), "sneaking up on it is not a bow");
        assertFalse(WildlifeRules.bowing(true, 0.97, 0.0, 12.0), "too far to be seen bowing");
    }

    @Test
    void anUnbowedStrangerIsWarnedThenAttacked() {
        assertEquals(WildlifeRules.Crowding.NONE, WildlifeRules.crowding(true, 1.0, -1), "respected");
        assertEquals(WildlifeRules.Crowding.NONE, WildlifeRules.crowding(false, 4.0, -1), "keeping its distance");
        assertEquals(WildlifeRules.Crowding.WARN, WildlifeRules.crowding(false, 1.0, -1), "first time: warned");
        assertEquals(WildlifeRules.Crowding.NONE, WildlifeRules.crowding(false, 1.0, 10), "a beat to back off");
        assertEquals(WildlifeRules.Crowding.ATTACK, WildlifeRules.crowding(false, 1.0, 60), "then an attack");
        assertEquals(WildlifeRules.Crowding.WARN,
                WildlifeRules.crowding(false, 1.0, WildlifeRules.WARNING_MEMORY_TICKS + 1), "long ago: warned again");
    }

    @Test
    void aPhoenixNeverStrikesTheKillingBlow() {
        assertEquals(3.0f, WildlifeRules.phoenixStrikeDamage(20.0f, 3.0f), 1.0e-6f);
        assertEquals(1.5f, WildlifeRules.phoenixStrikeDamage(2.5f, 3.0f), 1.0e-6f, "stops at one heart");
        assertEquals(0.0f, WildlifeRules.phoenixStrikeDamage(1.0f, 3.0f), 1.0e-6f, "a creature on one heart is left alone");
        assertEquals(0.0f, WildlifeRules.phoenixStrikeDamage(0.5f, 3.0f), 1.0e-6f, "never negative");
    }

    @Test
    void aPhoenixSingsForSomeoneBadlyHurtOrJustStruck() {
        assertTrue(WildlifeRules.inDanger(8f, 20f, Integer.MAX_VALUE), "at or below half health");
        assertTrue(WildlifeRules.inDanger(20f, 20f, 40), "struck two seconds ago");
        assertFalse(WildlifeRules.inDanger(20f, 20f, 200), "unhurt and left alone");
    }

    // The Niffler's treasure ranking is data now: NifflerTreasureTagsTest.

    // ── the Bowtruckle ──

    @Test
    void aBowtruckleVanishesOnlyWhenStillAgainstBarkAndIdle() {
        int still = at.koopro.wizardsandbeasts.entity.beast.BowtruckleEntity.CAMOUFLAGE_AFTER;
        assertTrue(WildlifeRules.camouflaged(still, true, false));
        assertFalse(WildlifeRules.camouflaged(still - 10, true, false), "not still long enough");
        assertFalse(WildlifeRules.camouflaged(still, false, false), "no bark beside it");
        assertFalse(WildlifeRules.camouflaged(still * 10, true, true), "busy defending or picking a lock");
    }

    // ── shedding and trees ──

    @Test
    void sheddingWaitsForTheIntervalAndForTheLastSignToBeTaken() {
        assertTrue(WildlifeRules.shedDue(0, false));
        assertFalse(WildlifeRules.shedDue(1, false));
        assertFalse(WildlifeRules.shedDue(0, true), "one tuft at a time in a clearing");
    }

    @Test
    void aBowtruckleCountsOnlyItsOwnTree() {
        assertTrue(WildlifeRules.partOfHomeTree(WildlifeRules.HOME_TREE_RADIUS * WildlifeRules.HOME_TREE_RADIUS));
        assertFalse(WildlifeRules.partOfHomeTree((WildlifeRules.HOME_TREE_RADIUS + 1) * (WildlifeRules.HOME_TREE_RADIUS + 1)));
    }

    // ── lycanthropy ──

    @Test
    void onlyATransformedWerewolfUnderAFullMoonInfectsAHuman() {
        assertTrue(WildlifeRules.infects(true, true, true, true, false, 2f));
        assertFalse(WildlifeRules.infects(false, true, true, true, false, 2f), "the server turned infection off");
        assertFalse(WildlifeRules.infects(true, false, true, true, false, 2f), "a werewolf in human form");
        assertFalse(WildlifeRules.infects(true, true, false, true, false, 2f), "no full moon");
        assertFalse(WildlifeRules.infects(true, true, true, false, false, 2f), "not human");
        assertFalse(WildlifeRules.infects(true, true, true, true, true, 2f), "already cursed");
        assertFalse(WildlifeRules.infects(true, true, true, true, false, 0f), "the bite drew no blood");
    }
}
