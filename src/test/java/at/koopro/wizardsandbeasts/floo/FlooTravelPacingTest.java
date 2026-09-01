package at.koopro.wizardsandbeasts.floo;

import at.koopro.wizardsandbeasts.Config;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * How often a wizard may be taken by the Network, and how long the fire holds them first.
 *
 * <p>Both numbers are configurable and both have a shipped default that the rest of the system is
 * tuned against, so what is pinned here is the relationship between them rather than the values on
 * their own — a cooldown shorter than the stagger it is supposed to be felt as would simply not be
 * noticed, and a windup longer than the hearth's own life would make travel impossible.
 */
class FlooTravelPacingTest {

    private final int originalCooldown = Config.flooTravelCooldownTicks;
    private final int originalWindup = Config.flooDepartureWindupTicks;

    @AfterEach
    void restoreConfig() {
        Config.flooTravelCooldownTicks = originalCooldown;
        Config.flooDepartureWindupTicks = originalWindup;
    }

    @Test
    void theShippedCooldownIsThreeSeconds() {
        assertEquals(60, originalCooldown);
    }

    @Test
    void aNegativeCooldownReadsAsNone() {
        // Config clamps at 0, but the accessor must not trust that: a field read before the config
        // load event holds whatever the class initialiser left, and a negative cooldown would
        // subtract into a permanent refusal.
        Config.flooTravelCooldownTicks = -40;
        assertEquals(0, FlooTravelHandler.cooldownTicks());
    }

    @Test
    void zeroCooldownIsAllowedAndMeansBackToBackTravel() {
        Config.flooTravelCooldownTicks = 0;
        assertEquals(0, FlooTravelHandler.cooldownTicks());
    }

    @Test
    void theCooldownOutlastsNothingLongerThanTheArrivalStagger() {
        // The cooldown is meant to be felt AS the stagger, not as a separate wait after it. Slowness
        // on a clean arrival runs 50 ticks and DISORIENTED runs 100; sitting between them means a
        // player is free again at about the moment they have their feet back.
        assertTrue(originalCooldown >= 50,
                "a cooldown shorter than the arrival slowness would never be noticed");
        assertTrue(originalCooldown <= 100,
                "a cooldown outlasting the disorientation would read as an arbitrary lockout");
    }

    @Test
    void theWindupFitsInsideAFreshlyLitHearth() {
        // The departure window is measured against a hearth that has to still be burning at the end
        // of it. A windup longer than the hearth's own life would make every hop cancel itself.
        assertTrue(FlooDeparture.windupTicks()
                        < at.koopro.wizardsandbeasts.block.floo.FlooFireplaceBlockEntity.litTimeoutTicks(),
                "a departure must be able to finish before the hearth goes cold");
    }

    @Test
    void aNegativeWindupReadsAsTheDefaultRatherThanAsInstantTravel() {
        // Zero is a legitimate setting and means "teleport on confirm". A negative value is not a
        // setting at all, and must not be mistaken for one - the difference matters because zero
        // deliberately bypasses the whole departure window.
        Config.flooDepartureWindupTicks = -1;
        assertEquals(FlooDeparture.DEFAULT_WINDUP_TICKS, FlooDeparture.windupTicks());
    }

    @Test
    void zeroWindupIsHonouredAsInstantTravel() {
        Config.flooDepartureWindupTicks = 0;
        assertEquals(0, FlooDeparture.windupTicks());
    }
}
