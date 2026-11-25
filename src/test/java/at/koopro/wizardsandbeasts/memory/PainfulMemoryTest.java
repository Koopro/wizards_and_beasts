package at.koopro.wizardsandbeasts.memory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * A12 (owner, 2026-11-25, option 1): painful memories add up apart from happy ones, and a Swooping Evil's sting takes
 * the newest painful one only.
 */
class PainfulMemoryTest {

    @Test
    void painfulAndHappyAreSummedApart() {
        PlayerMemoryData data = new PlayerMemoryData();
        data.addMemory(new MemoryEntry(MemoryType.HAPPY, 0.6f, "levelup", 10));
        data.addMemory(new MemoryEntry(MemoryType.PAINFUL, 0.5f, "dementor", 20));
        data.addMemory(new MemoryEntry(MemoryType.PAINFUL, 0.7f, "cruciatus", 30));
        assertEquals(0.6f, data.happyIntensitySum(), 1e-6);
        assertEquals(1.2f, data.intensitySum(MemoryType.PAINFUL), 1e-6);
    }

    @Test
    void theStingTakesTheNewestPainfulMemoryAndLeavesTheRest() {
        PlayerMemoryData data = new PlayerMemoryData();
        data.addMemory(new MemoryEntry(MemoryType.PAINFUL, 0.8f, "companion:a", 100));
        data.addMemory(new MemoryEntry(MemoryType.HAPPY, 0.3f, "sleep", 300));
        data.addMemory(new MemoryEntry(MemoryType.PAINFUL, 0.4f, "near_death", 200));
        MemoryEntry gone = data.forgetNewest(MemoryType.PAINFUL).orElseThrow();
        assertEquals("near_death", gone.source());
        assertEquals(0.8f, data.intensitySum(MemoryType.PAINFUL), 1e-6);
        assertEquals(0.3f, data.happyIntensitySum(), 1e-6);
    }

    @Test
    void nothingToForgetIsNothingForgotten() {
        PlayerMemoryData data = new PlayerMemoryData();
        data.addMemory(new MemoryEntry(MemoryType.HAPPY, 0.3f, "sleep", 1));
        assertTrue(data.forgetNewest(MemoryType.PAINFUL).isEmpty());
        assertEquals(1, data.memories().size());
    }

    @Test
    void thePatronusPenaltyIsBounded() {
        // Three days of the worst kinds still leave the charm possible: the penalty caps below the forming threshold.
        float sum = 0.8f + 0.7f + 0.5f + 0.4f + 0.8f + 0.7f;
        assertEquals(MemoryService.MAX_PAIN_PENALTY, Math.min(MemoryService.MAX_PAIN_PENALTY, sum * MemoryService.PAIN_SCALE));
        assertTrue(MemoryService.MAX_PAIN_PENALTY < 100f - 15f);
    }
}
