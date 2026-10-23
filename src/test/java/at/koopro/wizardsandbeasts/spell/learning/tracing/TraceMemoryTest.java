package at.koopro.wizardsandbeasts.spell.learning.tracing;

import com.google.gson.JsonElement;
import com.mojang.serialization.JsonOps;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The saved trace memory: waits count down, expired ones are dropped, and it survives a save. */
class TraceMemoryTest {

    @Test
    void aWaitCountsDownToNothing() {
        TraceMemory memory = TraceMemory.EMPTY.withRetry("a:x", 200, 100);
        assertEquals(100, memory.waitFor("a:x", 100));
        assertEquals(1, memory.waitFor("a:x", 199));
        assertEquals(0, memory.waitFor("a:x", 200));
        assertEquals(0, memory.waitFor("a:y", 100));
    }

    @Test
    void expiredWaitsAreDroppedWhenANewOneIsAdded() {
        TraceMemory memory = TraceMemory.EMPTY.withRetry("a:old", 150, 100).withRetry("a:new", 400, 300);
        assertFalse(memory.retryAt().containsKey("a:old"));
        assertTrue(memory.retryAt().containsKey("a:new"));
    }

    @Test
    void itSurvivesASaveAndOldSavesReadEmpty() {
        TraceMemory memory = TraceMemory.EMPTY.withRetry("a:x", 500, 0).withSteadyHand(true);
        JsonElement json = TraceMemory.CODEC.encodeStart(JsonOps.INSTANCE, memory).getOrThrow();
        assertEquals(memory, TraceMemory.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow());
        assertEquals(TraceMemory.EMPTY, TraceMemory.CODEC.parse(JsonOps.INSTANCE,
                com.google.gson.JsonParser.parseString("{}")).getOrThrow());
    }
}
