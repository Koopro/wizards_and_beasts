package at.koopro.wizardsandbeasts.entity.azkaban;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The aura's ladder, without a world: nearer is worse, a swarm adds one, nothing past the cold. */
class DementorAuraTest {

    @Test
    void theBandsFollowDistance() {
        assertEquals(2, DementorAura.band(0.0));
        assertEquals(2, DementorAura.band(DementorAura.DRAIN_RADIUS));
        assertEquals(1, DementorAura.band(DementorAura.DRAIN_RADIUS + 0.01));
        assertEquals(1, DementorAura.band(DementorAura.FEAR_RADIUS));
        assertEquals(0, DementorAura.band(DementorAura.FEAR_RADIUS + 0.01));
        assertEquals(0, DementorAura.band(DementorAura.COLD_RADIUS));
        assertEquals(DementorAura.NONE, DementorAura.band(DementorAura.COLD_RADIUS + 0.01));
        assertEquals(DementorAura.NONE, DementorAura.band(DementorAura.SENSE_RADIUS));
    }

    @Test
    void nearerIsNeverKinder() {
        int last = DementorAura.NONE;
        for (double d = DementorAura.SENSE_RADIUS; d >= 0; d -= 0.25) {
            int amp = DementorAura.amplifier(d, 1);
            assertTrue(amp >= last, "stepping closer to " + d + " lowered the chill");
            last = amp;
        }
    }

    @Test
    void aSwarmDeepensByOneAndCapsAtThree() {
        assertEquals(0, DementorAura.amplifier(12, DementorAura.SWARM_SIZE - 1));
        assertEquals(1, DementorAura.amplifier(12, DementorAura.SWARM_SIZE));
        assertEquals(2, DementorAura.amplifier(6, DementorAura.SWARM_SIZE));
        assertEquals(3, DementorAura.amplifier(2, DementorAura.SWARM_SIZE));
        assertEquals(DementorAura.MAX_AMPLIFIER, DementorAura.amplifier(0, 50), "no pile-up past the cap");
        assertEquals(DementorAura.NONE, DementorAura.amplifier(20, 50), "a swarm far off is still not felt as cold");
    }

    @Test
    void theChillOutlastsOnePulseButNotMany() {
        // Refreshed every 20 ticks: it must survive to the next pulse, and end within seconds of leaving.
        assertTrue(DementorAura.CHILL_TICKS > 20 && DementorAura.CHILL_TICKS <= 100, "chill lasts " + DementorAura.CHILL_TICKS);
    }
}
