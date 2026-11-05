package at.koopro.wizardsandbeasts.creature.rules;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Day and night creatures spawn by the time of day, not by shade (Beast Renaissance D2). The old rule read stored sky
 * light, which is 15 under open sky at midnight too, so night creatures appeared only in caves and day creatures
 * under the stars.
 */
class SpawnTimeTest {

    @Test
    void aNightCreatureNeedsTheNight() {
        // Open meadow at midnight: dark outside, the sky dimmed to about 4.
        assertTrue(SpawnTime.fits(false, true, 4), "a mooncalf on an open meadow at midnight");
        // The same meadow at noon.
        assertFalse(SpawnTime.fits(false, false, 15), "a night creature on an open meadow at noon");
        // A cave at noon is dark, but it is not night: the old rule let this through, and that was the bug.
        assertFalse(SpawnTime.fits(false, false, 0), "a night creature in a cave by day");
        // Night, but standing in torchlight.
        assertFalse(SpawnTime.fits(false, true, 12), "a night creature beside a torch");
    }

    @Test
    void aDayCreatureNeedsTheDay() {
        assertTrue(SpawnTime.fits(true, false, 15), "a bowtruckle in a forest at noon");
        assertFalse(SpawnTime.fits(true, true, 15), "a day creature at midnight: stored sky light is still 15");
        assertFalse(SpawnTime.fits(true, false, 3), "a day creature in a dark cave at noon");
    }

    @Test
    void theLineIsInclusiveBothWays() {
        assertTrue(SpawnTime.fits(true, false, SpawnTime.LIGHT_LINE));
        assertTrue(SpawnTime.fits(false, true, SpawnTime.LIGHT_LINE));
    }
}
