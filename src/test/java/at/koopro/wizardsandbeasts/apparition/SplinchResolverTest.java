package at.koopro.wizardsandbeasts.apparition;

import at.koopro.wizardsandbeasts.apparition.charge.ApparitionWindow;
import at.koopro.wizardsandbeasts.apparition.charge.Destabilization;
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
        Destabilization everything = new Destabilization(9, true, true, true, true, false);
        assertEquals(ApparitionWindow.FORCED_DISCHARGE,
                SplinchResolver.inflate(ApparitionWindow.FORCED_DISCHARGE, everything));
    }

    // ── inflation ──

    @Test
    void additiveTermsAccumulate() {
        Destabilization messy = new Destabilization(1, true, true, true, false, true);
        // 0 + 4 (one hit) + 2 (moving) + 3 (submerged) + 2 (encumbered)
        assertEquals(11, SplinchResolver.inflate(0, messy));
    }

    @Test
    void additiveTermsLandBeforeMultiplicativeOnes() {
        Destabilization movingSideAlong = new Destabilization(0, true, false, false, true, true);
        // (1 + 2) * 2 = 6. Multiplying first would give 1*2 + 2 = 4, a whole rung lower.
        assertEquals(6, SplinchResolver.inflate(1, movingSideAlong));
        assertEquals(SplinchTier.MAJOR, SplinchResolver.resolve(1, movingSideAlong));
    }

    @Test
    void theUnlicensedTaxIsAppliedLastAndRounds() {
        Destabilization unlicensed = new Destabilization(0, true, false, false, false, false);
        // (1 + 2) * 1.25 = 3.75, rounded to 4 — still the top of the minor rung.
        assertEquals(4, SplinchResolver.inflate(1, unlicensed));
        assertEquals(SplinchTier.MINOR, SplinchResolver.resolve(1, unlicensed));
    }

    @Test
    void bothMultipliersCompound() {
        Destabilization worst = new Destabilization(1, false, false, false, true, false);
        // (0 + 4) * 2 * 1.25 = 10
        assertEquals(10, SplinchResolver.inflate(0, worst));
    }

    @Test
    void aCleanReleaseWhileComposedStaysClean() {
        assertEquals(SplinchTier.CLEAN, SplinchResolver.resolve(0, calm()));
    }

    @Test
    void aSingleHitOnAnAnchoredAbortLandsExactlyOnMinor() {
        // The anchored damage abort resolves at a raw miss of zero and lets the hit's own +4 place it.
        Destabilization struckOnce = new Destabilization(1, false, false, false, false, true);
        assertEquals(SplinchTier.MINOR, SplinchResolver.resolve(0, struckOnce));
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
