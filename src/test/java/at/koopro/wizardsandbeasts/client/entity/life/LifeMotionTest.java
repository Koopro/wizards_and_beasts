package at.koopro.wizardsandbeasts.client.entity.life;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LifeMotionTest {

    @Test
    void neckAndHeadSharesAddUpToTheWholeTurn() {
        for (int neck = 0; neck <= 4; neck++) {
            float total = LifeMotion.neckShare(neck) * neck + LifeMotion.headShare(neck);
            assertEquals(1f, total, 1e-6f, "neck count " + neck);
        }
    }

    @Test
    void lookWrapsBeforeItClamps() {
        assertEquals(-10f, LifeMotion.clampLook(350f, LifeMotion.MAX_YAW), 1e-4f);
        assertEquals(LifeMotion.MAX_YAW, LifeMotion.clampLook(120f, LifeMotion.MAX_YAW), 1e-4f);
        assertEquals(-LifeMotion.MAX_PITCH, LifeMotion.clampLook(-89f, LifeMotion.MAX_PITCH), 1e-4f);
    }

    @Test
    void aBlinkClosesFullyAndIsShort() {
        assertEquals(0f, LifeMotion.pulse(-0.5f));
        assertEquals(0.5f, LifeMotion.pulse(1f), 1e-6f);
        assertEquals(1f, LifeMotion.pulse(2.5f));
        assertEquals(0f, LifeMotion.pulse(LifeMotion.BLINK_TICKS));
    }

    @Test
    void eyesAreOpenMostOfTheTimeAndEveryCreatureBlinks() {
        for (long seed : new long[] {0L, 1L, -7L, 123456789L, Long.MIN_VALUE + 3}) {
            int shut = 0;
            int samples = 20 * 60 * 4; // a minute in quarter ticks
            float max = 0f;
            for (int i = 0; i < samples; i++) {
                float c = LifeMotion.blinkClosure(i / 4f, seed);
                assertTrue(c >= 0f && c <= 1f);
                if (c > 0.5f) shut++;
                max = Math.max(max, c);
            }
            assertEquals(1f, max, 1e-6f, "seed " + seed + " never closes its eyes");
            assertTrue(shut < samples * 0.12, "seed " + seed + " has its eyes shut too often: " + shut);
        }
    }

    @Test
    void twoCreaturesDoNotBlinkInStep() {
        int differ = 0;
        for (int t = 0; t < 1200; t++) {
            if (LifeMotion.blinkClosure(t, 11L) != LifeMotion.blinkClosure(t, 12345L)) differ++;
        }
        assertTrue(differ > 0);
    }

    @Test
    void biggerAndSleepingAnimalsBreatheSlower() {
        assertTrue(LifeMotion.breathPeriod(3f, false) > LifeMotion.breathPeriod(0.5f, false));
        assertTrue(LifeMotion.breathPeriod(1f, true) > LifeMotion.breathPeriod(1f, false));
    }

    @Test
    void breathingStaysSubtle() {
        for (int t = 0; t < 400; t++) {
            float awake = LifeMotion.breathScale(t, 40f, false, 9L);
            float asleep = LifeMotion.breathScale(t, 60f, true, 9L);
            assertTrue(Math.abs(awake - 1f) <= 0.0181f);
            assertTrue(Math.abs(asleep - 1f) <= 0.0351f);
        }
        assertNotEquals(LifeMotion.breathScale(10f, 40f, false, 1L), LifeMotion.breathScale(10f, 40f, false, 700L));
    }

    @Test
    void earsFlickOutwardOneAtATime() {
        boolean sawLeft = false;
        boolean sawRight = false;
        for (int t = 0; t < 2000; t++) {
            float[] f = LifeMotion.earFlick(t + 0.5f, 42L);
            assertTrue(f[0] >= 0f && f[1] <= 0f);
            assertTrue(f[0] == 0f || f[1] == 0f);
            sawLeft |= f[0] > 0f;
            sawRight |= f[1] < 0f;
        }
        assertTrue(sawLeft && sawRight);
    }

    @Test
    void approachNeverOvershoots() {
        assertEquals(1f, LifeMotion.approach(0.95f, 1f, 0.15f));
        assertEquals(0.85f, LifeMotion.approach(1f, 0f, 0.15f), 1e-6f);
        assertEquals(0f, LifeMotion.approach(0.1f, 0f, 0.15f));
    }
}
