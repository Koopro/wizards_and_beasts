package at.koopro.wizardsandbeasts.heritage;

import at.koopro.wizardsandbeasts.heritage.werewolf.WerewolfRules;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The moon wheel and the night boundary.
 *
 * <p>Both are pure functions of the world clock precisely so they can be pinned here — the rest of
 * the handler needs a server, a level and a player, and the part that decides <em>whether tonight is
 * the night</em> is the part worth being certain about. A phase off by one turns the full moon into
 * the waning gibbous and the mechanic never fires on the night players expect.
 */
class WerewolfMoonRulesTest {

    private static final long DAY = 24000L;

    /** Vanilla indexes {@code MOON_BRIGHTNESS_PER_PHASE} with this, and its first entry is 1.0F. */
    @Test
    void dayZeroIsTheFullMoon() {
        assertEquals(0, WerewolfRules.moonPhase(0L));
        assertEquals(0, WerewolfRules.moonPhase(DAY - 1));
    }

    @Test
    void thePhaseWheelAdvancesOncePerDayAndWrapsAtEight() {
        for (int day = 0; day < 8; day++) {
            assertEquals(day, WerewolfRules.moonPhase(day * DAY),
                    "day " + day + " should be phase " + day);
        }
        assertEquals(0, WerewolfRules.moonPhase(8 * DAY), "the wheel wraps after eight days");
        assertEquals(3, WerewolfRules.moonPhase(11 * DAY));
    }

    @Test
    void theFullMoonReturnsEveryEighthDay() {
        for (int cycle = 0; cycle < 5; cycle++) {
            assertEquals(0, WerewolfRules.moonPhase(cycle * 8L * DAY));
        }
    }

    /** A negative world time is reachable with {@code /time set} and must not produce a negative phase. */
    @Test
    void aNegativeClockStillYieldsAPhaseInRange() {
        for (long t = -5 * DAY; t < 0; t += DAY / 3) {
            int phase = WerewolfRules.moonPhase(t);
            assertTrue(phase >= 0 && phase < 8, "phase out of range at t=" + t + ": " + phase);
        }
    }

    // ── night boundary ──

    @Test
    void dayIsNotNight() {
        assertFalse(WerewolfRules.isNight(0L), "dawn");
        assertFalse(WerewolfRules.isNight(6000L), "noon");
        assertFalse(WerewolfRules.isNight(12299L), "the last tick of day");
    }

    @Test
    void nightRunsFromDuskToTheNextDawn() {
        assertTrue(WerewolfRules.isNight(12300L), "the first tick of night");
        assertTrue(WerewolfRules.isNight(18000L), "midnight");
        assertTrue(WerewolfRules.isNight(DAY - 1), "the last tick before dawn");
    }

    /**
     * The boundary matches {@code ObscurialTierRules.isDaytime}, which treats {@code 0..12300} as day.
     * Two different answers to "is it night" in one mod is a bug nobody can reproduce.
     */
    @Test
    void theNightBoundaryMatchesTheObscurialRules() {
        assertFalse(WerewolfRules.isNight(12299L));
        assertTrue(WerewolfRules.isNight(12300L));
    }

    @Test
    void nightRepeatsEveryDay() {
        assertTrue(WerewolfRules.isNight(DAY + 13000L), "the second night");
        assertFalse(WerewolfRules.isNight(DAY + 6000L), "the second noon");
        assertTrue(WerewolfRules.isNight(100 * DAY + 13000L));
    }

    @Test
    void aNegativeClockStillDistinguishesDayFromNight() {
        assertTrue(WerewolfRules.isNight(-DAY + 13000L));
        assertFalse(WerewolfRules.isNight(-DAY + 6000L));
    }

    /**
     * The combination that actually matters: a full-moon <em>night</em> is one window per eight days,
     * not one day in eight and not every night.
     */
    @Test
    void fullMoonNightsAreOneWindowPerEightDays() {
        int transformingWindows = 0;
        for (long day = 0; day < 8; day++) {
            long dusk = day * DAY + 13000L;
            if (WerewolfRules.moonPhase(dusk) == 0 && WerewolfRules.isNight(dusk)) {
                transformingWindows++;
            }
        }
        assertEquals(1, transformingWindows, "exactly one night in eight should turn a werewolf");
    }

    @Test
    void theFullMoonDaytimeIsNotATransformingWindow() {
        long fullMoonNoon = 6000L;
        assertEquals(0, WerewolfRules.moonPhase(fullMoonNoon), "still the full-moon day");
        assertFalse(WerewolfRules.isNight(fullMoonNoon),
                "the moon has to be up, not merely full");
    }
}
