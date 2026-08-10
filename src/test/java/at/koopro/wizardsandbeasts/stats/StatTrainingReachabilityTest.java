package at.koopro.wizardsandbeasts.stats;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Guards that every trainable stat can actually be trained, and records what that costs.
 *
 * <p>This is the test that would have caught the shipped bug: {@code PlayerStatsAPI.addTrainingProgress}
 * had no callers anywhere in the mod, so PRECISION, WILLPOWER and REFLEXES could only move through
 * five one-shot milestones. REFLEXES had no milestone at all and was pinned at 0 for every player,
 * forever. Nothing logged and nothing failed — the feature was simply unreachable, the same failure
 * shape as {@code WandBondReachabilityTest}.
 *
 * <p>The event counts below are pinned rather than bounded so that retuning a constant in
 * {@link StatTraining} has to be a deliberate edit here too, with the new grind length visible in
 * the diff.
 */
class StatTrainingReachabilityTest {

    /**
     * Replays {@code PlayerStatsAPI.addTrainingProgress}'s accumulator without needing a Player.
     *
     * <p>The real method's inner {@code while} can only ever run once per call, because
     * {@link StatTrainingScaler#scale} tops out at the raw amount and every raw amount in
     * {@link StatTraining} is well under 1.0.
     */
    private static int eventsToReach(float rawPerEvent, int targetStat) {
        int stat = 0;
        float accumulator = 0f;
        int events = 0;
        while (stat < targetStat) {
            float scaled = StatTrainingScaler.scale(rawPerEvent, stat);
            if (scaled <= 0f) {
                fail("training stalled at stat " + stat + " — raw " + rawPerEvent
                        + " scales to zero, so " + targetStat + " is unreachable");
            }
            accumulator += scaled;
            events++;
            if (accumulator >= 1.0f) {
                accumulator -= 1.0f;
                stat++;
            }
            if (events > 5_000_000) {
                fail("no convergence towards stat " + targetStat + " after 5M events");
            }
        }
        return events;
    }

    @Test
    void everyTrainableStatCanLeaveZero() {
        assertEquals(10, eventsToReach(StatTraining.PRECISION_PER_SPELL_HIT, 1));
        assertEquals(3, eventsToReach(StatTraining.REFLEXES_PER_DEFLECT, 1));
        assertEquals(5, eventsToReach(StatTraining.WILLPOWER_PER_IMPERIUS_ENDURED, 1));
        assertEquals(2, eventsToReach(StatTraining.WILLPOWER_PER_MIND_DEFENDED, 1));
    }

    @Test
    void precisionGrindLength() {
        assertEquals(307, eventsToReach(StatTraining.PRECISION_PER_SPELL_HIT, 25));
        assertEquals(819, eventsToReach(StatTraining.PRECISION_PER_SPELL_HIT, 50));
        assertEquals(1965, eventsToReach(StatTraining.PRECISION_PER_SPELL_HIT, 75));
    }

    @Test
    void reflexesGrindLength() {
        assertEquals(88, eventsToReach(StatTraining.REFLEXES_PER_DEFLECT, 25));
        assertEquals(234, eventsToReach(StatTraining.REFLEXES_PER_DEFLECT, 50));
        assertEquals(561, eventsToReach(StatTraining.REFLEXES_PER_DEFLECT, 75));
    }

    @Test
    void willpowerGrindLength() {
        assertEquals(154, eventsToReach(StatTraining.WILLPOWER_PER_IMPERIUS_ENDURED, 25));
        assertEquals(410, eventsToReach(StatTraining.WILLPOWER_PER_IMPERIUS_ENDURED, 50));

        assertEquals(62, eventsToReach(StatTraining.WILLPOWER_PER_MIND_DEFENDED, 25));
        assertEquals(164, eventsToReach(StatTraining.WILLPOWER_PER_MIND_DEFENDED, 50));
    }

    @Test
    void theRelativeCostOfEachSourceMatchesHowOftenItCanHappen() {
        // Landing a spell is the commonest event, so it must be the cheapest per event; repelling a
        // Legilimens is the rarest, so it must be the dearest. Getting this backwards would make the
        // grind land on whichever action is easiest to farm.
        assertTrue(StatTraining.PRECISION_PER_SPELL_HIT < StatTraining.WILLPOWER_PER_IMPERIUS_ENDURED);
        assertTrue(StatTraining.WILLPOWER_PER_IMPERIUS_ENDURED < StatTraining.REFLEXES_PER_DEFLECT);
        assertTrue(StatTraining.REFLEXES_PER_DEFLECT < StatTraining.WILLPOWER_PER_MIND_DEFENDED);
    }

    @Test
    void oneHundredIsAsymptoticByDesign() {
        // StatTrainingScaler returns exactly 0 at 100, so the last point can never be earned. This
        // is intended — it is recorded here so nobody re-derives it as a bug — and it is why the
        // grind figures above stop at 75. Admin `/wandb stats set` remains the only route to 100.
        assertEquals(0f, StatTrainingScaler.scale(1.0f, 100), 1.0e-6f);
        assertTrue(StatTrainingScaler.scale(1.0f, 99) > 0f,
                "99 must still be trainable — only the final point is out of reach");
    }
}
