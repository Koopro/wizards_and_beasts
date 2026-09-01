package at.koopro.wizardsandbeasts.entity.broom;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The broom rules that decide whether a flight hurts, kept honest.
 *
 * <p>The starter broom and the Firebolt Supreme are used as the two poles throughout because the bug
 * this class was written to fix only shows up as a <em>spread</em>: a single fixed denominator cannot
 * describe both, and the old one described neither. Every severity assertion below is made at both
 * ends, because a rule that is right for one broom and wrong for the other is the exact failure that
 * shipped.
 */
class BroomFlightRulesTest {

    /** `broom` / `cleansweep_seven`: maxSpeed 0.35 x boost 1.3. */
    private static final float SLOW_BROOM_TOP = 0.455f;
    /** `firebolt_supreme`: maxSpeed 1.05 x boost 2.5. */
    private static final float FAST_BROOM_TOP = 2.625f;
    /** The fixed denominator that used to be used for every broom. */
    private static final float OLD_FIXED_DENOMINATOR = 1.15f * 1.45f;

    private static final float EPS = 1e-4f;

    // ── severity ────────────────────────────────────────────────────────────────────────────────

    @Test
    void headOnAtFullSpeed_isASevereCrash_onEveryBroom() {
        for (float top : new float[] {SLOW_BROOM_TOP, FAST_BROOM_TOP}) {
            float severity = BroomFlightRules.impactSeverity(top, 0f, top, true, false);
            assertEquals(1.0f, severity, EPS,
                    "flying flat out into a wall must read as a full-severity crash at top=" + top);
            assertTrue(severity >= BroomTuning.SEVERE_IMPACT_THRESHOLD);
        }
    }

    /**
     * The regression this class exists for. Under the old fixed denominator the starter broom's
     * hardest possible crash scored 0.27 — below {@link BroomTuning#MINOR_IMPACT_THRESHOLD}, so it
     * could not register any impact at all, into any wall, ever.
     */
    @Test
    void slowBroom_couldNotRegisterAnyImpactUnderTheOldDenominator() {
        float oldSeverity = SLOW_BROOM_TOP / OLD_FIXED_DENOMINATOR;
        assertTrue(oldSeverity < BroomTuning.MINOR_IMPACT_THRESHOLD,
                "precondition: the old maths really did score the starter broom below every threshold");

        float now = BroomFlightRules.impactSeverity(SLOW_BROOM_TOP, 0f, SLOW_BROOM_TOP, true, false);
        assertTrue(now >= BroomTuning.SEVERE_IMPACT_THRESHOLD,
                "the starter broom's hardest crash must now be a crash");
    }

    /**
     * The same bug in the other direction: the Firebolt Supreme crossed the severe threshold at 64%
     * of its own top speed, so ordinary cruising was a fatal impact.
     */
    @Test
    void fastBroom_wasFatalAtCruiseUnderTheOldDenominator() {
        float cruise = FAST_BROOM_TOP * 0.64f;
        assertTrue(cruise / OLD_FIXED_DENOMINATOR >= BroomTuning.SEVERE_IMPACT_THRESHOLD,
                "precondition: the old maths really did make 64% throttle a severe crash");

        float now = BroomFlightRules.impactSeverity(cruise, 0f, FAST_BROOM_TOP, true, false);
        assertTrue(now < BroomTuning.SEVERE_IMPACT_THRESHOLD,
                "64% of this broom's own top speed must not be a fatal crash");
    }

    @Test
    void severityIsTheSameFractionOnEveryBroom_atTheSameFractionOfItsCeiling() {
        float slow = BroomFlightRules.impactSeverity(SLOW_BROOM_TOP * 0.5f, 0f, SLOW_BROOM_TOP, true, false);
        float fast = BroomFlightRules.impactSeverity(FAST_BROOM_TOP * 0.5f, 0f, FAST_BROOM_TOP, true, false);
        assertEquals(slow, fast, EPS,
                "half throttle must feel the same on a Cleansweep as on a Firebolt Supreme");
    }

    @Test
    void verticalImpactUsesDescentRate_notForwardSpeed() {
        // Straight down, no forward motion at all: the old signature scored this exactly zero.
        float severity = BroomFlightRules.impactSeverity(0f, FAST_BROOM_TOP, FAST_BROOM_TOP, false, true);
        assertEquals(BroomTuning.VERTICAL_IMPACT_WEIGHT, severity, EPS);
        assertTrue(severity > BroomTuning.MODERATE_IMPACT_THRESHOLD,
                "falling out of the sky must hurt even with no forward speed — it used to score 0");
        // Still short of severe: MAX_CRASH_DAMAGE is reserved for flying flat out into a cliff face,
        // and a fall is deliberately scored softer than a wall at the same speed.
        assertTrue(severity < BroomTuning.SEVERE_IMPACT_THRESHOLD);
    }

    @Test
    void verticalCountsForLessThanHeadOn_atTheSameSpeed() {
        float head = BroomFlightRules.impactSeverity(FAST_BROOM_TOP, 0f, FAST_BROOM_TOP, true, false);
        float down = BroomFlightRules.impactSeverity(0f, FAST_BROOM_TOP, FAST_BROOM_TOP, false, true);
        assertTrue(down < head, "hitting the floor should be softer than hitting a wall");
    }

    @Test
    void cornerImpactExceedsEitherAxisButIsNotTheirSum() {
        float speed = FAST_BROOM_TOP * 0.6f;
        float head = BroomFlightRules.impactSeverity(speed, 0f, FAST_BROOM_TOP, true, false);
        float down = BroomFlightRules.impactSeverity(0f, speed, FAST_BROOM_TOP, false, true);
        float corner = BroomFlightRules.impactSeverity(speed, speed, FAST_BROOM_TOP, true, true);

        assertTrue(corner > head, "a corner is worse than the wall alone");
        assertTrue(corner < head + down, "a corner must not be scored as both impacts added together");
    }

    @Test
    void noCollisionFlags_meansNoSeverity() {
        assertEquals(0f,
                BroomFlightRules.impactSeverity(FAST_BROOM_TOP, FAST_BROOM_TOP, FAST_BROOM_TOP, false, false),
                EPS);
    }

    @Test
    void zeroCeiling_doesNotDivideByZero() {
        assertEquals(0f, BroomFlightRules.impactSeverity(1f, 1f, 0f, true, true), EPS);
    }

    // ── gentle landing ──────────────────────────────────────────────────────────────────────────

    @Test
    void settlingOntoFlatGround_isALanding_notAnImpact() {
        assertTrue(BroomFlightRules.isGentleLanding(0f, 0.05f, FAST_BROOM_TOP));
        assertTrue(BroomFlightRules.isGentleLanding(0f, BroomTuning.GENTLE_LANDING_MAX_DESCENT, FAST_BROOM_TOP));
    }

    @Test
    void divingIntoTheGround_isNotALanding() {
        assertFalse(BroomFlightRules.isGentleLanding(
                0f, BroomTuning.GENTLE_LANDING_MAX_DESCENT + 0.01f, FAST_BROOM_TOP));
    }

    @Test
    void skimmingTheGroundAtSpeed_isNotALanding() {
        float tooFast = FAST_BROOM_TOP * (BroomTuning.GENTLE_LANDING_MAX_SPEED_RATIO + 0.05f);
        assertFalse(BroomFlightRules.isGentleLanding(tooFast, 0.05f, FAST_BROOM_TOP),
                "still travelling forward at speed is a skid, not a landing");
    }

    @Test
    void theLandingRuleScalesWithTheBroom_likeSeverityDoes() {
        // The same fraction of each broom's ceiling has to give the same answer, or the starter
        // broom could never land and the fast one could land at any speed.
        float slowAtLimit = SLOW_BROOM_TOP * BroomTuning.GENTLE_LANDING_MAX_SPEED_RATIO;
        float fastAtLimit = FAST_BROOM_TOP * BroomTuning.GENTLE_LANDING_MAX_SPEED_RATIO;
        assertTrue(BroomFlightRules.isGentleLanding(slowAtLimit, 0.05f, SLOW_BROOM_TOP));
        assertTrue(BroomFlightRules.isGentleLanding(fastAtLimit, 0.05f, FAST_BROOM_TOP));
    }

    @Test
    void climbingIntoACeiling_isNotALanding() {
        // Negative descent = rising. The absolute value is what matters; hitting a ceiling hard
        // must not be waved through as a soft touchdown.
        assertFalse(BroomFlightRules.isGentleLanding(0f, -0.6f, FAST_BROOM_TOP));
    }

    // ── weak gravity ────────────────────────────────────────────────────────────────────────────

    @Test
    void withNoInput_theBroomSinks() {
        float v = 0f;
        for (int tick = 0; tick < 5; tick++) {
            v = BroomFlightRules.applyWeakGravity(v, 0.008f);
        }
        assertTrue(v < 0f, "a broom nobody is holding up must come down");
        assertEquals(-0.04f, v, EPS);
    }

    @Test
    void sinkIsCappedWellShortOfFreeFall() {
        float v = 0f;
        for (int tick = 0; tick < 500; tick++) {
            v = BroomFlightRules.applyWeakGravity(v, 0.02f);
        }
        assertEquals(-BroomTuning.TERMINAL_SINK_SPEED, v, EPS,
                "letting go over a long drop must settle, not accelerate without limit");
    }

    @Test
    void aClimbingBroomIsStillSlowedByGravity_butNotReversedInOneTick() {
        float v = BroomFlightRules.applyWeakGravity(0.24f, 0.008f);
        assertEquals(0.232f, v, EPS);
        assertTrue(v > 0f, "one tick of weak gravity must not cancel an ascent");
    }

    // ── speed ratio ─────────────────────────────────────────────────────────────────────────────

    @Test
    void speedRatioIsClampedAndSignless() {
        assertEquals(0f, BroomFlightRules.speedRatio(0f, 1f), EPS);
        assertEquals(1f, BroomFlightRules.speedRatio(1f, 1f), EPS);
        assertEquals(1f, BroomFlightRules.speedRatio(5f, 1f), EPS, "boost must not exceed a full bar");
        assertEquals(0.5f, BroomFlightRules.speedRatio(-0.5f, 1f), EPS, "reversing still reads as speed");
        assertEquals(0f, BroomFlightRules.speedRatio(1f, 0f), EPS, "no ceiling, no divide by zero");
    }

    // ── dismount ────────────────────────────────────────────────────────────────────────────────

    @Test
    void dismountTriesDownwardFirst() {
        int[][] offsets = BroomFlightRules.dismountOffsets();
        assertTrue(offsets.length > 0);
        assertArrayEqualsInt(new int[] {0, -1, 0}, offsets[0],
                "the first place to look for a rider is the ground under the broom");
    }

    @Test
    void dismountTriesUpwardLast_becauseUpIsHowADismountBecomesAFall() {
        int[][] offsets = BroomFlightRules.dismountOffsets();
        assertArrayEqualsInt(new int[] {0, 1, 0}, offsets[offsets.length - 1],
                "up must be the last candidate tried, never an early one");

        List<int[]> list = Arrays.asList(offsets);
        long upwards = list.stream().filter(o -> o[1] > 0).count();
        assertEquals(1L, upwards, "exactly one upward candidate, and it is the last resort");
    }

    @Test
    void dismountCandidatesAreUnique() {
        Set<String> seen = new HashSet<>();
        List<String> duplicates = new ArrayList<>();
        for (int[] offset : BroomFlightRules.dismountOffsets()) {
            String key = offset[0] + "," + offset[1] + "," + offset[2];
            if (!seen.add(key)) {
                duplicates.add(key);
            }
        }
        assertTrue(duplicates.isEmpty(), "duplicate dismount candidates re-test the same block: " + duplicates);
    }

    @Test
    void dismountStaysWithinItsSearchBounds() {
        for (int[] offset : BroomFlightRules.dismountOffsets()) {
            assertTrue(Math.abs(offset[0]) <= BroomTuning.DISMOUNT_SEARCH_RADIUS);
            assertTrue(Math.abs(offset[2]) <= BroomTuning.DISMOUNT_SEARCH_RADIUS);
            assertTrue(offset[1] >= -BroomTuning.DISMOUNT_DROP_SEARCH && offset[1] <= 1,
                    "a dismount is a courtesy scan, not a teleport");
        }
    }

    @Test
    void theBroomsOwnPositionIsACandidate_soAGroundedBroomAlwaysHasAnAnswer() {
        boolean hasOrigin = Arrays.stream(BroomFlightRules.dismountOffsets())
                .anyMatch(o -> o[0] == 0 && o[1] == 0 && o[2] == 0);
        assertTrue(hasOrigin);
    }

    private static void assertArrayEqualsInt(int[] expected, int[] actual, String message) {
        assertEquals(Arrays.toString(expected), Arrays.toString(actual), message);
    }
}
