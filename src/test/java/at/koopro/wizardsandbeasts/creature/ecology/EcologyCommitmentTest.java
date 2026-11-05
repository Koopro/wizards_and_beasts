package at.koopro.wizardsandbeasts.creature.ecology;

import org.junit.jupiter.api.Test;

import static at.koopro.wizardsandbeasts.creature.ecology.EcologyCapability.*;
import static at.koopro.wizardsandbeasts.creature.ecology.EcologyIntent.*;
import static at.koopro.wizardsandbeasts.creature.ecology.EcologyNeed.*;
import static at.koopro.wizardsandbeasts.creature.ecology.EcologyScenario.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Holding a decision: interruption, minimum and maximum durations, hysteresis, cooldowns and failure backoff. */
class EcologyCommitmentTest {

    private static final EcologyCommitment.Cooldowns NONE_COOLING = (intent, now) -> false;

    private static EcologyBrain.Decision d(EcologyIntent intent, float urgency) {
        return new EcologyBrain.Decision(intent, null, urgency);
    }

    private static EcologyIntent arbitrate(EcologyBrain.Decision current, long held, boolean sustained,
                                           EcologyBrain.Decision candidate) {
        return EcologyCommitment.arbitrate(current, 1000, sustained, candidate, 1000 + held, NONE_COOLING).intent();
    }

    @Test
    void aMoreUrgentBandInterruptsAtOnce() {
        assertEquals(FLEE_PREDATOR, arbitrate(d(FORAGE, 0.9f), 1, true, d(FLEE_PREDATOR, 1)));
        assertEquals(WARN_INTRUDER, arbitrate(d(SLEEP, 0.6f), 1, true, d(WARN_INTRUDER, 0.9f)));
        // A warning escalates to a charge the moment the intruder comes on.
        assertEquals(DEFEND_TERRITORY, arbitrate(d(WARN_INTRUDER, 0.8f), 0, true, d(DEFEND_TERRITORY, 0.9f)));
    }

    @Test
    void aSameBandCandidateWaitsForTheMinimumAndThenNeedsAMargin() {
        assertEquals(FORAGE, arbitrate(d(FORAGE, 0.6f), 50, true, d(DRINK, 0.95f)), "held under its 100-tick minimum");
        assertEquals(FORAGE, arbitrate(d(FORAGE, 0.6f), 200, true, d(DRINK, 0.7f)), "not more pressing by the margin");
        assertEquals(DRINK, arbitrate(d(FORAGE, 0.6f), 200, true, d(DRINK, 0.8f)));
    }

    @Test
    void somethingThatStillMakesSenseIsNotDroppedForSomethingLessUrgent() {
        assertEquals(FLEE_PREDATOR, arbitrate(d(FLEE_PREDATOR, 1), 100, true, d(NONE, 0)));
        assertEquals(DEFEND_TERRITORY, arbitrate(d(DEFEND_TERRITORY, 0.9f), 100, true, d(FORAGE, 1)));
    }

    @Test
    void somethingThatNoLongerMakesSenseIsDroppedEvenInsideItsMinimum() {
        assertEquals(NONE, arbitrate(d(FORAGE, 0.6f), 5, false, d(NONE, 0)));
        assertEquals(REGROUP, arbitrate(d(FORAGE, 0.6f), 5, false, d(REGROUP, 0.5f)));
    }

    @Test
    void nothingIsHeldPastItsMaximumEvenIfTheBrainWouldPickItAgain() {
        assertEquals(NONE, arbitrate(d(GROOM, 0.2f), GROOM.maxTicks(), true, d(GROOM, 0.2f)));
        assertEquals(GROOM, arbitrate(d(GROOM, 0.2f), GROOM.maxTicks() - 1, true, d(GROOM, 0.2f)));
        assertTrue(EcologyCommitment.timedOut(GROOM, GROOM.maxTicks()));
        assertFalse(EcologyCommitment.timedOut(NONE, Long.MAX_VALUE));
    }

    @Test
    void aCandidateCoolingDownCountsAsNothing() {
        EcologyCommitment.Cooldowns drinkCooling = (intent, now) -> intent == DRINK;
        assertEquals(NONE, EcologyCommitment.arbitrate(d(NONE, 0), 0, false, d(DRINK, 0.9f), 10, drinkCooling).intent());
        assertEquals(FORAGE, EcologyCommitment.arbitrate(d(FORAGE, 0.6f), 0, true, d(DRINK, 0.9f), 500, drinkCooling).intent());
    }

    @Test
    void failuresBackOffLongerEachTimeAndASuccessForgivesThem() {
        EcologyState state = new EcologyState();
        state.failed(DRINK, 0);
        assertTrue(state.cooling(DRINK, DRINK.failCooldown() - 1));
        assertFalse(state.cooling(DRINK, DRINK.failCooldown()));
        state.failed(DRINK, 1000);
        assertTrue(state.cooling(DRINK, 1000 + 2L * DRINK.failCooldown() - 1), "second failure: twice as long");
        for (int i = 0; i < 10; i++) {
            state.failed(DRINK, 5000);
        }
        assertFalse(state.cooling(DRINK, 5000 + 8L * DRINK.failCooldown()), "capped at eight times");
        state.succeeded(DRINK);
        assertEquals(0, state.failures(DRINK));
        state.ended(GROOM, 0, false);
        assertTrue(state.cooling(GROOM, GROOM.endCooldown() - 1), "a groom is not repeated straight away");
        state.ended(FORAGE, 0, false);
        assertFalse(state.cooling(FORAGE, 1), "a meal may follow a meal");
    }

    /**
     * The point of all this: a prey animal with a predator pacing at the edge of its flee radius (11.5, 12.5, 11.5…
     * blocks, against a 12-block radius) bolts once and keeps running, where the bare policy would start and stop on
     * every look.
     */
    @Test
    void aThreatAtTheEdgeOfRangeDoesNotMakeTheCreatureFlicker() {
        EcologyState cooldowns = new EcologyState();
        EcologyBrain.Decision current = EcologyBrain.Decision.NONE;
        long heldSince = 0;
        int switches = 0;
        int rawFlips = 0;
        EcologyIntent lastRaw = NONE;
        for (int look = 0; look < 40; look++) {
            long now = look * 20L;
            EcologyScenario seen = creature(PREY).sees(predatorAt(look % 2 == 0 ? 11.5 : 12.5));
            EcologyBrain.Decision candidate = seen.decision();
            if (candidate.intent() != lastRaw) {
                rawFlips++;
                lastRaw = candidate.intent();
            }
            boolean sustained = current.intent() != NONE && seen.sustains(current.intent());
            EcologyBrain.Decision next = EcologyCommitment.arbitrate(current, heldSince, sustained, candidate, now, cooldowns);
            if (next.intent() != current.intent()) {
                switches++;
                if (current.intent() != NONE) {
                    cooldowns.ended(current.intent(), now, EcologyCommitment.timedOut(current.intent(), now - heldSince));
                }
                heldSince = now;
            }
            current = next;
        }
        assertTrue(rawFlips >= 39, "the bare policy flips on every look: " + rawFlips);
        // Bolts, runs to its 300-tick maximum, rests 20 ticks, bolts again: a handful of changes in 800 ticks, not 40.
        assertTrue(switches <= 6, "committed creature changed its mind " + switches + " times");
    }

    @Test
    void aHungryCreatureFinishesItsMealBeforeWanderingOffToSomethingSlightlyMorePressing() {
        EcologyScenario eating = creature(GRAZER, HERD).feeling(HUNGER, 0.3f).seesForage(1).kinCentredAt(14);
        assertTrue(eating.sustains(FORAGE));
        assertEquals(REGROUP, eating.decision().intent());
        assertEquals(FORAGE, EcologyCommitment.arbitrate(d(FORAGE, 0.6f), 0, true, eating.decision(), 400, NONE_COOLING)
                .intent(), "regroup (0.5) is not more pressing than the meal by the margin");
    }
}
