package at.koopro.wizardsandbeasts.basilisk;

import net.minecraft.util.RandomSource;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BasiliskGrowthTest {

    @Test
    void hatchesSmallIsAdultAtFiveDaysAndStopsAtTheCap() {
        assertEquals(BasiliskGrowth.HATCHLING, BasiliskGrowth.size(0), 1e-6);
        assertEquals(1f, BasiliskGrowth.size(BasiliskGrowth.ADULT_TICKS), 1e-6);
        assertEquals(BasiliskGrowth.CAP, BasiliskGrowth.size(BasiliskGrowth.CAP_TICKS), 1e-6);
        assertEquals(BasiliskGrowth.CAP, BasiliskGrowth.size(BasiliskGrowth.CAP_TICKS * 10), 1e-6);
    }

    @Test
    void neverShrinks() {
        float last = 0f;
        for (long t = 0; t <= BasiliskGrowth.CAP_TICKS + BasiliskGrowth.DAY; t += 1_000) {
            float size = BasiliskGrowth.size(t);
            assertTrue(size >= last, "shrank at " + t);
            last = size;
        }
    }

    @Test
    void growsFastFirstThenSlowly() {
        // Half the hatchling-to-adult gain comes in well under half the five days...
        float halfway = BasiliskGrowth.HATCHLING + (1f - BasiliskGrowth.HATCHLING) / 2f;
        assertTrue(BasiliskGrowth.size(BasiliskGrowth.ADULT_TICKS / 3) > halfway);
        // ...and the first adult day adds more than the last day before the cap.
        float firstAdultDay = BasiliskGrowth.size(BasiliskGrowth.ADULT_TICKS + BasiliskGrowth.DAY) - 1f;
        float lastDay = BasiliskGrowth.CAP - BasiliskGrowth.size(BasiliskGrowth.CAP_TICKS - BasiliskGrowth.DAY);
        assertTrue(firstAdultDay > lastDay * 10);
    }

    @Test
    void aWildOneIsMetGrown() {
        RandomSource random = RandomSource.create(7);
        for (int i = 0; i < 100; i++) {
            long age = BasiliskGrowth.wildAge(random);
            assertTrue(age >= BasiliskGrowth.ADULT_TICKS && age <= 3 * BasiliskGrowth.ADULT_TICKS);
            assertTrue(BasiliskGrowth.size(age) >= 1f && BasiliskGrowth.size(age) < BasiliskGrowth.CAP);
        }
    }
}
