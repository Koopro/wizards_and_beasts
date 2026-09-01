package at.koopro.wizardsandbeasts.gillyweed;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The numbers a diver's life depends on.
 *
 * <p>{@link #theWarningFiresBeforeTheGillsClose} is the one that earns its keep: an off-by-one there
 * is a warning that arrives after the breathing stops, and the only way to find that out in play is
 * to drown at the bottom of the Black Lake wondering what went wrong.
 */
class GillyweedTest {

    @Test
    void theBriefsNumbersAreWhatShipped() {
        assertEquals(900, Gillyweed.DURATION_TICKS);
        assertEquals(45, Gillyweed.DURATION_TICKS / 20);
        assertEquals(3, Gillyweed.WARNING_TICKS / 20);
        assertEquals(10, Gillyweed.REFRESH_COOLDOWN_TICKS / 20);
    }

    @Test
    void theWarningFiresBeforeTheGillsClose() {
        assertFalse(Gillyweed.isFading(Gillyweed.WARNING_TICKS + 1),
                "four seconds out is not yet a warning");
        assertTrue(Gillyweed.isFading(Gillyweed.WARNING_TICKS),
                "the warning must start exactly three seconds out");
        assertTrue(Gillyweed.isFading(1), "the last tick is still a warning");
    }

    @Test
    void anExpiredEffectIsNotFading() {
        // Zero means gone. Reporting it as fading would leave the warning running for a diver who is
        // already drowning, which is worse than useless.
        assertFalse(Gillyweed.isFading(0));
        assertFalse(Gillyweed.isFading(-5));
    }

    @Test
    void theWarningIsAMeaningfulFractionOfTheDive() {
        assertTrue(Gillyweed.WARNING_TICKS < Gillyweed.DURATION_TICKS,
                "a warning as long as the effect is not a warning");
        assertTrue(Gillyweed.WARNING_TICKS * 10 < Gillyweed.DURATION_TICKS,
                "the warning should be the tail of the dive, not a third of it");
    }

    @Test
    void everyTickOfTheDiveIsEitherSwimmingOrWarning() {
        int warning = 0;
        for (int remaining = Gillyweed.DURATION_TICKS; remaining >= 1; remaining--) {
            if (Gillyweed.isFading(remaining)) {
                warning++;
            }
        }
        assertEquals(Gillyweed.WARNING_TICKS, warning,
                "exactly the warning window should be spent warning");
    }

    @Test
    void theSwimBonusIsRealButNotAbsurd() {
        assertTrue(Gillyweed.SWIM_BONUS > 0.0,
                "the brief asks for swim speed, not just breathing");
        assertTrue(Gillyweed.SWIM_BONUS <= 2.0,
                "a gilled wizard should swim well, not teleport; got " + Gillyweed.SWIM_BONUS);
    }

    @Test
    void refreshingCostsSomethingButOnlyAfterwards() {
        // The cooldown lands on expiry, not on eating — that is what makes topping up mid-dive a real
        // choice rather than something you simply never do.
        assertTrue(Gillyweed.REFRESH_COOLDOWN_TICKS > 0,
                "a free refresh makes a stack of Gillyweed permanent aquatic life");
        assertTrue(Gillyweed.REFRESH_COOLDOWN_TICKS < Gillyweed.DURATION_TICKS,
                "the penalty should be a pause, not a second dive's worth of waiting");
    }
}
