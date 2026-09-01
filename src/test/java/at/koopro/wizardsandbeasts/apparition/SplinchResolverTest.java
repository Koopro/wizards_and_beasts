package at.koopro.wizardsandbeasts.apparition;

import at.koopro.wizardsandbeasts.apparition.charge.ApparitionWindow;
import at.koopro.wizardsandbeasts.apparition.charge.Destabilization;
import at.koopro.wizardsandbeasts.apparition.splinch.WindupDamageMode;
import at.koopro.wizardsandbeasts.apparition.splinch.SplinchResolver;
import at.koopro.wizardsandbeasts.apparition.splinch.SplinchTier;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The splinch ladder: rung boundaries, inflation ordering, and the forced discharge. */
class SplinchResolverTest {

    private static Destabilization calm() {
        return Destabilization.NONE;
    }

    // ── rung boundaries ──

    @Test
    void aCleanReleaseCostsNothing() {
        assertEquals(SplinchTier.CLEAN, SplinchResolver.resolve(0));
    }

    @Test
    void oneThroughFourIsMinor() {
        assertEquals(SplinchTier.MINOR, SplinchResolver.resolve(1));
        assertEquals(SplinchTier.MINOR, SplinchResolver.resolve(4));
    }

    @Test
    void fiveThroughTwelveIsMajor() {
        assertEquals(SplinchTier.MAJOR, SplinchResolver.resolve(5));
        assertEquals(SplinchTier.MAJOR, SplinchResolver.resolve(12));
    }

    @Test
    void thirteenAndAboveIsCatastrophic() {
        assertEquals(SplinchTier.CATASTROPHIC, SplinchResolver.resolve(13));
        assertEquals(SplinchTier.CATASTROPHIC, SplinchResolver.resolve(500));
    }

    @Test
    void neverReleasingIsAlwaysCatastrophic() {
        assertEquals(SplinchTier.CATASTROPHIC, SplinchResolver.resolve(ApparitionWindow.FORCED_DISCHARGE));
        assertEquals(SplinchTier.CATASTROPHIC,
                SplinchResolver.resolve(ApparitionWindow.FORCED_DISCHARGE, calm()));
    }

    @Test
    void aForcedDischargeIsNotInflatedAndCannotOverflow() {
        Destabilization everything = new Destabilization(9, true, true, true, true, true, false);
        assertEquals(ApparitionWindow.FORCED_DISCHARGE,
                SplinchResolver.inflate(ApparitionWindow.FORCED_DISCHARGE, everything));
    }

    // ── inflation ──

    @Test
    void additiveTermsAccumulate() {
        Destabilization messy = new Destabilization(1, true, true, true, false, false, true);
        // 0 + 4 (one hit) + 2 (moving) + 3 (submerged) + 2 (encumbered)
        assertEquals(11, SplinchResolver.inflate(0, messy));
    }

    @Test
    void additiveTermsLandBeforeMultiplicativeOnes() {
        Destabilization movingSideAlong = new Destabilization(0, true, false, false, false, true, true);
        // (1 + 2) * 2 = 6. Multiplying first would give 1*2 + 2 = 4, a whole rung lower.
        assertEquals(6, SplinchResolver.inflate(1, movingSideAlong));
        assertEquals(SplinchTier.MAJOR, SplinchResolver.resolve(1, movingSideAlong));
    }

    @Test
    void theUnlicensedTaxIsAppliedLastAndRounds() {
        Destabilization unlicensed = new Destabilization(0, true, false, false, false, false, false);
        // (1 + 2) * 1.25 = 3.75, rounded to 4 — still the top of the minor rung.
        assertEquals(4, SplinchResolver.inflate(1, unlicensed));
        assertEquals(SplinchTier.MINOR, SplinchResolver.resolve(1, unlicensed));
    }

    @Test
    void bothMultipliersCompound() {
        Destabilization worst = new Destabilization(1, false, false, false, false, true, false);
        // (0 + 4) * 2 * 1.25 = 10
        assertEquals(10, SplinchResolver.inflate(0, worst));
    }

    @Test
    void travellingHungryCostsTheSameAsTravellingCarelessly() {
        Destabilization famished = new Destabilization(0, false, false, false, true, false, true);
        // 1 + 2 (famished). Deliberately the movement weight: both are states the wizard chose to be in.
        assertEquals(3, SplinchResolver.inflate(1, famished));
        assertEquals(SplinchResolver.MOVEMENT_MISS, SplinchResolver.FAMISHED_MISS);
    }

    /**
     * The unlicensed tax multiplies, so it can push a sloppy release up a rung but can never manufacture a
     * splinch out of a perfect one. "No licence but forced" is explicitly never the rule.
     */
    @Test
    void beingUnlicensedNeverSplinchesAPerfectRelease() {
        Destabilization unlicensedButComposed =
                new Destabilization(0, false, false, false, false, false, false);
        assertEquals(0, SplinchResolver.inflate(0, unlicensedButComposed));
        assertEquals(SplinchTier.CLEAN, SplinchResolver.resolve(0, unlicensedButComposed));
    }

    @Test
    void aCleanReleaseWhileComposedStaysClean() {
        assertEquals(SplinchTier.CLEAN, SplinchResolver.resolve(0, calm()));
    }

    @Test
    void aSingleHitOnAnAnchoredAbortLandsExactlyOnMinor() {
        // The anchored damage abort resolves at a raw miss of zero and lets the hit's own +4 place it.
        Destabilization struckOnce = new Destabilization(1, false, false, false, false, false, true);
        assertEquals(SplinchTier.MINOR, SplinchResolver.resolve(0, struckOnce));
    }


    // ── the under-fire floor ──

    private static SplinchTier floored(SplinchTier computed, int hits, boolean anchored, boolean carrying) {
        return SplinchResolver.floorForWindupDamage(
                computed, hits, anchored, carrying, WindupDamageMode.HYBRID);
    }

    @Test
    void anUnharassedJumpIsLeftExactlyWhereTheLadderPutIt() {
        for (SplinchTier computed : SplinchTier.values()) {
            assertEquals(computed, floored(computed, 0, false, false));
            assertEquals(computed, floored(computed, 0, true, true),
                    "with no hits taken, neither anchoring nor a passenger floors anything");
        }
    }

    @Test
    void windupDamage_floorsToMinorAndStillArrives() {
        SplinchTier tier = floored(SplinchTier.CLEAN, 1, false, false);

        assertEquals(SplinchTier.MINOR, tier);
        assertTrue(tier.arrives(), "one hit taxes the jump; it does not cost the journey");
        assertTrue(tier.damage() > 0.0f);
    }

    @Test
    void anchoredWindupDamage_floorsToMajorAndStillArrives() {
        SplinchTier tier = floored(SplinchTier.CLEAN, 1, true, false);

        assertEquals(SplinchTier.MAJOR, tier);
        assertTrue(tier.arrives(), "arriving anchored and injured is possible, and expensive");
    }

    @Test
    void twoHits_floorToMajorEvenOnABlink() {
        assertEquals(SplinchTier.MAJOR, floored(SplinchTier.CLEAN, 2, false, false));
        assertEquals(SplinchTier.MAJOR, floored(SplinchTier.CLEAN, 9, false, false));
    }

    @Test
    void windupDamageWhileCarrying_floorsToCatastrophe() {
        SplinchTier tier = floored(SplinchTier.CLEAN, 1, false, true);

        assertEquals(SplinchTier.CATASTROPHIC, tier);
        assertFalse(tier.arrives(), "nobody is dragged through a fight; the journey is lost");
    }

    /** The floor is a floor. An outcome the ladder already put higher is left alone. */
    @Test
    void theFloorNeverImprovesAnOutcome() {
        assertEquals(SplinchTier.CATASTROPHIC, floored(SplinchTier.CATASTROPHIC, 1, false, false));
        assertEquals(SplinchTier.MAJOR, floored(SplinchTier.MAJOR, 1, false, false));
        for (SplinchTier computed : SplinchTier.values()) {
            for (int hits = 0; hits <= 3; hits++) {
                for (boolean anchored : new boolean[] {false, true}) {
                    for (boolean carrying : new boolean[] {false, true}) {
                        SplinchTier result = floored(computed, hits, anchored, carrying);
                        assertTrue(result.ordinal() >= computed.ordinal(),
                                "the floor made " + computed + " better: " + result);
                    }
                }
            }
        }
    }

    @Test
    void cancelMode_costsTheJourneyOnTheFirstHit() {
        SplinchTier tier = SplinchResolver.floorForWindupDamage(
                SplinchTier.CLEAN, 1, false, false, WindupDamageMode.CANCEL);

        assertEquals(SplinchTier.CATASTROPHIC, tier);
        assertFalse(tier.arrives(), "CANCEL means no teleport, and CATASTROPHIC is the rung that says so");
    }

    @Test
    void lenientMode_floorsNothingAtAll() {
        assertEquals(SplinchTier.CLEAN,
                SplinchResolver.floorForWindupDamage(
                        SplinchTier.CLEAN, 4, true, true, WindupDamageMode.LENIENT));
    }

    /** The shipped default, pinned so a change to it is a deliberate edit rather than a drift. */
    @Test
    void hybridIsWhatShips() {
        assertEquals(WindupDamageMode.HYBRID, ApparitionRules.windupDamageMode());
    }

    // ── tier parameters ──

    @Test
    void onlyACatastropheKeepsYouWhereYouStarted() {
        assertTrue(SplinchTier.CLEAN.arrives());
        assertTrue(SplinchTier.MINOR.arrives());
        assertTrue(SplinchTier.MAJOR.arrives());
        assertFalse(SplinchTier.CATASTROPHIC.arrives());
    }

    @Test
    void onlyACatastropheLocksYouOut() {
        assertEquals(0, SplinchTier.MAJOR.lockoutTicks());
        assertEquals(6000, SplinchTier.CATASTROPHIC.lockoutTicks());
    }

    @Test
    void aCleanArrivalAppliesNoWound() {
        assertFalse(SplinchTier.CLEAN.isSplinch());
        assertFalse(SplinchTier.CLEAN.appliesEffect());
        assertEquals(0.0f, SplinchTier.CLEAN.damage(), 1e-6);
    }
}
