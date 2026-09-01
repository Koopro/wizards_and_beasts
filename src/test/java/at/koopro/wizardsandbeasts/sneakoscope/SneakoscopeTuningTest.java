package at.koopro.wizardsandbeasts.sneakoscope;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The Sneakoscope's feel, expressed as the things a player would notice if they broke: a top that
 * never stops turning, a top that turns when nothing is wrong, an arrow that points the wrong way,
 * and an alarm that whites out the screen in a crowded room.
 */
class SneakoscopeTuningTest {

    private static final float EPS = 1e-4F;

    @Test
    void tier_bandsMatchTheDocumentedThresholds() {
        assertEquals(SneakoscopeTier.CALM, SneakoscopeTuning.tier(0));
        assertEquals(SneakoscopeTier.LOW, SneakoscopeTuning.tier(1));
        assertEquals(SneakoscopeTier.LOW, SneakoscopeTuning.tier(2));
        assertEquals(SneakoscopeTier.MEDIUM, SneakoscopeTuning.tier(3));
        assertEquals(SneakoscopeTier.MEDIUM, SneakoscopeTuning.tier(5));
        assertEquals(SneakoscopeTier.HIGH, SneakoscopeTuning.tier(6));
        assertEquals(SneakoscopeTier.HIGH, SneakoscopeTuning.tier(999));
    }

    @Test
    void aCalmSneakoscopeDoesNotMoveAtAll() {
        // The single most important property of the object: still means safe. A decorative idle
        // spin would make every reading unreadable.
        for (long millis = 0; millis < 10_000; millis += 137) {
            assertEquals(0.0f, SneakoscopeTuning.spinPhase(millis, SneakoscopeTier.CALM, false), EPS);
            assertEquals(0.0f, SneakoscopeTuning.spinPhase(millis, SneakoscopeTier.CALM, true), EPS);
            assertEquals(0, SneakoscopeTuning.spinFrame(
                    SneakoscopeTuning.spinPhase(millis, SneakoscopeTier.CALM, false)));
        }
    }

    @Test
    void spinPhase_staysInsideOneRevolutionForEveryTierAndTime() {
        for (SneakoscopeTier tier : SneakoscopeTier.values()) {
            for (long millis = 0; millis < 600_000; millis += 991) {
                float phase = SneakoscopeTuning.spinPhase(millis, tier, true);
                assertTrue(phase >= 0.0f && phase < 1.0f,
                        () -> "phase escaped [0,1) for " + tier + ": " + phase);
                int frame = SneakoscopeTuning.spinFrame(phase);
                assertTrue(frame >= 0 && frame < SneakoscopeTuning.SPIN_FRAMES,
                        () -> "frame " + frame + " is outside the dispatched model set");
            }
        }
    }

    @Test
    void spin_getsFasterWithEveryTierAndFasterAgainWhenFocused() {
        float low = SneakoscopeTuning.revolutionsPerSecond(SneakoscopeTier.LOW, false);
        float medium = SneakoscopeTuning.revolutionsPerSecond(SneakoscopeTier.MEDIUM, false);
        float high = SneakoscopeTuning.revolutionsPerSecond(SneakoscopeTier.HIGH, false);

        assertTrue(low > 0.0f, "one suspect must move the top");
        assertTrue(medium > low && high > medium, "each band must read as faster than the last");
        assertTrue(SneakoscopeTuning.revolutionsPerSecond(SneakoscopeTier.MEDIUM, true) > medium,
                "focused mode must wind the top up");
    }

    @Test
    void lowTierWobbles_soOneSuspectShiversRatherThanGlides() {
        // A pure rotation is periodic in exactly 1/rps seconds; the wobble breaks that period. If
        // this ever passes without the wobble term the LOW band has silently become a slow glide.
        float rps = SneakoscopeTuning.revolutionsPerSecond(SneakoscopeTier.LOW, false);
        long quarterTurnMillis = Math.round(250.0 / rps);
        float advance = SneakoscopeTuning.spinPhase(quarterTurnMillis, SneakoscopeTier.LOW, false)
                - SneakoscopeTuning.spinPhase(0L, SneakoscopeTier.LOW, false);

        assertNotEquals(0.25f, advance, EPS,
                "LOW must not advance as a clean quarter-turn; it is supposed to shiver");
    }

    @Test
    void focusedModeTradesReachForRefreshRate() {
        assertTrue(SneakoscopeTuning.scanRadius(true) < SneakoscopeTuning.scanRadius(false),
                "focus must cost reach");
        assertTrue(SneakoscopeTuning.scanIntervalTicks(true) < SneakoscopeTuning.scanIntervalTicks(false),
                "focus must buy refresh rate");
        assertEquals(SneakoscopeTuning.BASE_SCAN_RADIUS / 2.0, SneakoscopeTuning.scanRadius(true), EPS);
        assertEquals(SneakoscopeTuning.BASE_SCAN_INTERVAL_TICKS / 2,
                SneakoscopeTuning.scanIntervalTicks(true));
    }

    @Test
    void calmIsSilentAndEveryLoudTierWhirrsMoreOften() {
        assertEquals(0, SneakoscopeTuning.whirrIntervalTicks(SneakoscopeTier.CALM),
                "a calm Sneakoscope must make no sound at all");
        assertTrue(SneakoscopeTuning.whirrIntervalTicks(SneakoscopeTier.HIGH)
                        < SneakoscopeTuning.whirrIntervalTicks(SneakoscopeTier.MEDIUM),
                "HIGH is meant to read as continuous");
        assertTrue(SneakoscopeTuning.whirrIntervalTicks(SneakoscopeTier.MEDIUM)
                < SneakoscopeTuning.whirrIntervalTicks(SneakoscopeTier.LOW));
        assertTrue(SneakoscopeTuning.whirrVolume(SneakoscopeTier.HIGH)
                > SneakoscopeTuning.whirrVolume(SneakoscopeTier.LOW));
        // Pitch is applied on top of the sounds.json base of 1.0 and Minecraft clamps above 2.0.
        for (SneakoscopeTier tier : SneakoscopeTier.values()) {
            float pitch = SneakoscopeTuning.whirrPitch(tier);
            assertTrue(pitch > 0.0f && pitch <= 2.0f, () -> tier + " pitch " + pitch + " is out of range");
        }
    }

    @Test
    void onlyTheTopBandLightsTheScreen_andNeverPastTheCap() {
        for (int threats = 0; threats < SneakoscopeTuning.HIGH_THRESHOLD; threats++) {
            assertEquals(0.0f, SneakoscopeTuning.alarmPulseAlpha(1234, threats), EPS,
                    "the screen tint is reserved for six or more");
        }
        for (long millis = 0; millis < 20_000; millis += 53) {
            for (int threats = SneakoscopeTuning.HIGH_THRESHOLD; threats < 40; threats++) {
                float alpha = SneakoscopeTuning.alarmPulseAlpha(millis, threats);
                assertTrue(alpha > 0.0f && alpha <= SneakoscopeTuning.MAX_ALARM_ALPHA,
                        () -> "alarm alpha " + alpha + " escaped (0, "
                                + SneakoscopeTuning.MAX_ALARM_ALPHA + "]");
            }
        }
    }

    @Test
    void alarmPulses_ratherThanSittingAtOneBrightness() {
        float min = Float.MAX_VALUE;
        float max = -Float.MAX_VALUE;
        for (long millis = 0; millis < 2_000; millis += 20) {
            float alpha = SneakoscopeTuning.alarmPulseAlpha(millis, 8);
            min = Math.min(min, alpha);
            max = Math.max(max, alpha);
        }
        assertTrue(max - min > 0.02f, "a static red frame reads as a bug, not as an alarm");
    }

    @Test
    void bearing_roundTripsThroughItsOwnSectorConvention() {
        // Every sector must point back along the direction that produced it. This is the contract
        // between the server that measures the bearing and the client that draws the arrow.
        for (int sector = 0; sector < SneakoscopeTuning.BEARING_SECTORS; sector++) {
            double dx = SneakoscopeTuning.sectorDirectionX(sector);
            double dz = SneakoscopeTuning.sectorDirectionZ(sector);
            assertEquals(sector, SneakoscopeTuning.bearingSector(dx * 7.5, dz * 7.5),
                    "sector " + sector + " did not survive the round trip");
        }
    }

    @Test
    void bearing_isAbsentForSomethingStandingOnTopOfYou() {
        assertEquals(SneakoscopeTuning.NO_BEARING, SneakoscopeTuning.bearingSector(0.0, 0.0));
        assertTrue(!SneakoscopeTuning.hasBearing(SneakoscopeTuning.NO_BEARING));
    }

    @Test
    void bearing_snapsToTheNearestSectorRatherThanTruncating() {
        // Just past the boundary between sector 0 and sector 1 must round up, not floor to 0.
        double justOverHalf = Math.toRadians(SneakoscopeTuning.SECTOR_DEGREES * 0.6);
        assertEquals(1, SneakoscopeTuning.bearingSector(Math.cos(justOverHalf), Math.sin(justOverHalf)));

        double justUnderHalf = Math.toRadians(SneakoscopeTuning.SECTOR_DEGREES * 0.4);
        assertEquals(0, SneakoscopeTuning.bearingSector(Math.cos(justUnderHalf), Math.sin(justUnderHalf)));
    }

    @Test
    void bearing_wrapsRatherThanReportingASeventeenthSector() {
        // An angle a hair short of a full turn rounds to 16, which is not a sector.
        double almostFullTurn = Math.toRadians(359.0);
        int sector = SneakoscopeTuning.bearingSector(Math.cos(almostFullTurn), Math.sin(almostFullTurn));
        assertEquals(0, sector);
        assertTrue(SneakoscopeTuning.hasBearing(sector));
    }

    @Test
    void motesStartAtMedium_soAQuietSneakoscopeCanBeCarriedUnnoticed() {
        assertEquals(0, SneakoscopeTuning.orbitParticles(SneakoscopeTier.CALM));
        assertEquals(0, SneakoscopeTuning.orbitParticles(SneakoscopeTier.LOW));
        assertTrue(SneakoscopeTuning.orbitParticles(SneakoscopeTier.MEDIUM) > 0);
        assertTrue(SneakoscopeTuning.orbitParticles(SneakoscopeTier.HIGH)
                > SneakoscopeTuning.orbitParticles(SneakoscopeTier.MEDIUM));
    }
}
