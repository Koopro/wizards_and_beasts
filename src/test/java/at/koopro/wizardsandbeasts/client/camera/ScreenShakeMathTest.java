package at.koopro.wizardsandbeasts.client.camera;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Camera-kick behaviour that a screenshot cannot show but a player feels immediately: a shake that
 * outgrows the screen on a big spell, a fight two hundred blocks away rattling your aim, and — the
 * one that only surfaces after a while — a camera that never fully settles back to vanilla.
 */
class ScreenShakeMathTest {

    private static final float EPS = 1e-5F;

    @Test
    void amplitude_staysUnderTheCapEvenForAnAbsurdBurst() {
        float huge = ScreenShakeMath.amplitude(100_000, 0.0);

        assertTrue(huge <= ScreenShakeMath.MAX_AMPLITUDE_DEG,
                "shake must never exceed the cap; got " + huge);
    }

    @Test
    void amplitude_growsWithBurstSizeButSublinearly() {
        float small = ScreenShakeMath.amplitude(4, 0.0);
        float big = ScreenShakeMath.amplitude(64, 0.0);

        assertTrue(big > small, "a bigger burst must kick harder");
        assertTrue(big < small * 16.0F,
                "16x the particles must not mean 16x the shake, or big spells are unplayable");
    }

    @Test
    void falloff_isFullUpCloseAndZeroBeyondRange() {
        assertEquals(1.0F, ScreenShakeMath.falloff(0.0), EPS);
        assertEquals(1.0F, ScreenShakeMath.falloff(ScreenShakeMath.FULL_STRENGTH_RANGE), EPS);
        assertEquals(0.0F, ScreenShakeMath.falloff(ScreenShakeMath.FALLOFF_RANGE), EPS);
        assertEquals(0.0F, ScreenShakeMath.falloff(1_000.0), EPS,
                "a distant fight must not touch the camera at all");
    }

    @Test
    void falloff_decreasesMonotonicallyWithDistance() {
        float previous = Float.MAX_VALUE;
        for (double d = 0.0; d <= ScreenShakeMath.FALLOFF_RANGE + 5.0; d += 0.5) {
            float here = ScreenShakeMath.falloff(d);
            assertTrue(here <= previous,
                    "falloff must never rise with distance; jumped at " + d);
            previous = here;
        }
    }

    @Test
    void decay_reachesExactlyZeroSoTheCameraIsHandedBack() {
        float peak = ScreenShakeMath.MAX_AMPLITUDE_DEG;

        assertEquals(0.0F, ScreenShakeMath.decay(peak, 10.0F, 10.0F), EPS,
                "a spent shake must be exactly zero, not merely small");
        assertEquals(0.0F, ScreenShakeMath.decay(peak, 99.0F, 10.0F), EPS);
        assertEquals(0.0F, ScreenShakeMath.decay(peak, 0.0F, 0.0F), EPS,
                "a zero-length shake must not divide by zero into a permanent offset");
    }

    @Test
    void decay_fadesMonotonicallyFromItsPeak() {
        float peak = ScreenShakeMath.MAX_AMPLITUDE_DEG;
        float previous = Float.MAX_VALUE;
        for (float t = 0.0F; t <= 10.0F; t += 0.25F) {
            float here = ScreenShakeMath.decay(peak, t, 10.0F);
            assertTrue(here <= previous, "shake must never grow mid-decay; jumped at t=" + t);
            previous = here;
        }
    }

    @Test
    void offset_isBoundedByTheCurrentAmplitude() {
        for (float t = 0.0F; t < 20.0F; t += 0.13F) {
            float o = ScreenShakeMath.offset(1.5F, t, 1.0F, 1.0F);
            assertTrue(Math.abs(o) <= 1.5F + EPS,
                    "offset must stay within the amplitude; got " + o + " at t=" + t);
        }
    }

    @Test
    void offset_isZeroOnceTheShakeIsSpent() {
        assertEquals(0.0F, ScreenShakeMath.offset(0.0F, 3.7F, 1.0F, 1.0F), EPS);
    }

    @Test
    void offset_startsAtZeroOnEveryAxis() {
        // The regression this exists for: pitch used to carry a quarter-cycle phase offset, so it
        // evaluated to full amplitude on the very first frame. The camera snapped the instant a
        // spell landed — a pop, not a shake. Both axes must begin exactly where the player left
        // the view.
        assertEquals(0.0F, ScreenShakeMath.offset(2.0F, 0.0F, 1.0F, 1.0F), EPS,
                "yaw must start undeflected");
        assertEquals(0.0F,
                ScreenShakeMath.offset(2.0F, 0.0F, ScreenShakeMath.PITCH_FREQUENCY_RATIO, 1.0F), EPS,
                "pitch must start undeflected");
    }

    @Test
    void axes_doNotTraceACircle() {
        // Equal frequencies a quarter-cycle apart orbit the aim point, which reads as vertigo. With
        // distinct rates the two axes must drift out of any fixed relationship.
        float worstAgreement = 0.0F;
        for (float t = 0.5F; t < 12.0F; t += 0.25F) {
            float yaw = ScreenShakeMath.offset(1.0F, t, 1.0F, 1.0F);
            float pitch = ScreenShakeMath.offset(1.0F, t, ScreenShakeMath.PITCH_FREQUENCY_RATIO, 1.0F);
            worstAgreement = Math.max(worstAgreement, Math.abs(yaw * yaw + pitch * pitch - 1.0F));
        }
        assertTrue(worstAgreement > 0.25F,
                "yaw^2 + pitch^2 stayed near constant, so the view is orbiting rather than shaking");
    }

    @Test
    void direction_mirrorsCleanly() {
        float positive = ScreenShakeMath.offset(1.0F, 2.0F, 1.0F, 1.0F);
        float negative = ScreenShakeMath.offset(1.0F, 2.0F, 1.0F, -1.0F);

        assertEquals(-positive, negative, EPS,
                "flipping the per-impact direction must mirror the shake, not reshape it");
    }
}
