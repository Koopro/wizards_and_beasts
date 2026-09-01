package at.koopro.wizardsandbeasts.stats;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The accumulator's own contract, at the boundaries the grind-length tests never reach.
 *
 * <p>{@code StatTrainingReachabilityTest} walks it from 0 upwards and pins how long that takes. This
 * covers what happens at the edges: a point landing, several landing at once, the ceiling, and the
 * numbers the character sheet's "next point in N" readout is derived from.
 */
class StatTrainingAccumulatorTest {

    private static final float EPS = 1.0e-5f;

    @Test
    void progressBelowOneJustAccumulates() {
        StatTrainingScaler.Step step = StatTrainingScaler.apply(10, 0.2f, 0.1f);
        assertEquals(10, step.stat(), "the stat moved without a whole point being earned");
        assertEquals(0, step.gained());
        assertEquals(0.2f + StatTrainingScaler.scale(0.1f, 10), step.progress(), EPS);
    }

    @Test
    void crossingOneEarnsAPointAndCarriesTheRemainder() {
        // 0.95 in the bank plus a full-value event at stat 0 lands the point and keeps the change.
        StatTrainingScaler.Step step = StatTrainingScaler.apply(0, 0.95f, 0.10f);
        assertEquals(1, step.stat());
        assertEquals(1, step.gained());
        assertEquals(0.05f, step.progress(), EPS,
                "the fraction past the point was dropped — training silently lost");
        assertTrue(step.progress() < 1.0f, "the accumulator must never come back at or above 1");
    }

    @Test
    void oneLargeGrantCanCarrySeveralPointsAtOnce() {
        // Nothing in play grants this much, but an admin path or a future source could. Each point
        // must be counted, not just the last one, or the level-up notice under-reports.
        StatTrainingScaler.Step step = StatTrainingScaler.apply(0, 0f, 3.5f);
        assertTrue(step.gained() >= 3, "a multi-point grant reported only " + step.gained());
        assertEquals(step.gained(), step.stat(), "gained and the new value disagree from a base of 0");
    }

    /**
     * The invariant that keeps {@link StatTrainingScaler#apply} allowed to scale once per call.
     *
     * <p>An event is priced against the stat as it was before it, so a grant big enough to cross
     * several points buys the whole run at the cheap end's rate. That is only harmless because no
     * source in the mod is worth anywhere near a whole point: the loop can never run more than once
     * from real gameplay. If a future source is ever tuned past 1.0 this fails, and whoever does it
     * has to decide deliberately whether to re-price per point or to split the grant.
     */
    @Test
    void noRealTrainingSourceCanCrossMoreThanOnePointAtATime() {
        for (StatTraining.Source source : StatTraining.Source.values()) {
            assertTrue(source.rawAmount() < 1.0f,
                    source + " is worth " + source.rawAmount() + " raw — at or above 1.0 a single "
                            + "event crosses several points and is priced at the rate of the first");
            StatTrainingScaler.Step step = StatTrainingScaler.apply(0, 0.99f, source.rawAmount());
            assertTrue(step.gained() <= 1,
                    source + " earned " + step.gained() + " points from one event");
        }
    }

    @Test
    void theCeilingIsAHardStopAndBanksNothing() {
        StatTrainingScaler.Step step = StatTrainingScaler.apply(PlayerStatsData.MAX_VALUE, 0.5f, 10f);
        assertEquals(PlayerStatsData.MAX_VALUE, step.stat());
        assertEquals(0, step.gained());
        assertEquals(0f, step.progress(), EPS,
                "progress banked at the ceiling can never be spent, so a bar frozen part-full reads "
                        + "as training that stopped working");
    }

    @Test
    void aGrantHugeEnoughToOverflowStopsAtTheCeiling() {
        StatTrainingScaler.Step step = StatTrainingScaler.apply(99, 0f, 100_000f);
        assertEquals(PlayerStatsData.MAX_VALUE, step.stat());
        assertEquals(0f, step.progress(), EPS);
        assertEquals(1, step.gained(), "more points were reported than the range could hold");
    }

    @Test
    void anOutOfRangeStatIsClampedRatherThanTrusted() {
        assertEquals(PlayerStatsData.MAX_VALUE, StatTrainingScaler.apply(500, 0f, 1f).stat());
        assertEquals(0, StatTrainingScaler.apply(-5, 0f, 0.0f).stat());
    }

    @Test
    void negativeProgressAndNegativeAmountsCannotDrainAStat() {
        StatTrainingScaler.Step step = StatTrainingScaler.apply(40, -3f, -10f);
        assertEquals(40, step.stat(), "a negative grant moved the stat");
        assertTrue(step.progress() >= 0f, "the accumulator went negative");
    }

    /**
     * The "next point in N events" figure the character sheet shows must be the truth, not a guess:
     * replaying exactly N events has to land the point, and N-1 must not.
     *
     * <p>Checked across every source and every stat value whose count is small enough for the sheet to
     * print it at all. Beyond that {@code eventsToNextPoint} returns 0 and the bullet omits the
     * number, rather than promising a target the accumulator would not honour.
     */
    @Test
    void theNextPointEstimateIsExactEverywhereItIsShown() {
        int checked = 0;
        for (PlayerStat stat : PlayerStat.values()) {
            if (!stat.isTrainable()) continue;
            for (StatTraining.Source source : StatTraining.Source.forStat(stat)) {
                for (int value = 0; value < PlayerStatsData.MAX_VALUE; value++) {
                    for (float progress : new float[]{0f, 0.33f, 0.9f}) {
                        int predicted = StatReadout.eventsToNextPoint(value, progress,
                                source.rawAmount());
                        if (predicted <= 0) continue;
                        checked++;

                        assertEquals(0, replay(value, progress, source.rawAmount(), predicted - 1),
                                "the point landed early: " + source + " at " + value + " / " + progress);
                        assertEquals(1, replay(value, progress, source.rawAmount(), predicted),
                                "the point did not land on the predicted event: " + source
                                        + " at " + value + " / " + progress);
                    }
                }
            }
        }
        assertTrue(checked > 500, "only " + checked + " cases exercised — the sweep is not covering");
    }

    @Test
    void theSheetStopsCountingBeforeTheFigureStopsBeingUseful() {
        // Deep into the curve one point is tens of thousands of spell hits. Rather than print a
        // target nobody can hold, the count is suppressed and the bullet just names the source. This
        // pins that such a case exists, so the cap is not quietly doing nothing — and that the low
        // end, where the number is genuinely useful, still gets one.
        assertEquals(0, StatReadout.eventsToNextPoint(97, 0f, StatTraining.PRECISION_PER_SPELL_HIT),
                "the deep end of the curve should be reported as uncountable, not as a five-digit target");
        assertTrue(StatReadout.eventsToNextPoint(0, 0f, StatTraining.PRECISION_PER_SPELL_HIT) > 0,
                "the cheap end of the curve must still show a count");
    }

    @Test
    void thereIsNoNextPointAtTheCeiling() {
        assertEquals(0, StatReadout.eventsToNextPoint(PlayerStatsData.MAX_VALUE, 0.5f,
                StatTraining.PRECISION_PER_SPELL_HIT));
    }

    /** Total points earned by {@code events} identical events from the given starting position. */
    private static int replay(int stat, float progress, float raw, int events) {
        int gained = 0;
        for (int i = 0; i < events; i++) {
            StatTrainingScaler.Step step = StatTrainingScaler.apply(stat, progress, raw);
            stat = step.stat();
            progress = step.progress();
            gained += step.gained();
        }
        return gained;
    }
}
