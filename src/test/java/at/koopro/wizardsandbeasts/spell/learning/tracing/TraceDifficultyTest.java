package at.koopro.wizardsandbeasts.spell.learning.tracing;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TraceDifficultyTest {

    @Test
    void rowsGetHarderInOrder() {
        TraceDifficulty[] rows = TraceDifficulty.values();
        for (int i = 1; i < rows.length; i++) {
            TraceDifficulty prev = rows[i - 1];
            TraceDifficulty next = rows[i];
            assertTrue(next.halfWidth() <= prev.halfWidth(), () -> next + " is wider than " + prev);
            assertTrue(next.maxMeanDeviation() <= prev.maxMeanDeviation(), () -> next + " is looser than " + prev);
            assertTrue(next.retryDelayTicks() >= prev.retryDelayTicks(), () -> next + " retries sooner than " + prev);
        }
    }

    @Test
    void easyRowsNeverMakeAPlayerWait() {
        assertEquals(0, TraceDifficulty.BASIC.retryDelayTicks());
        assertEquals(0, TraceDifficulty.INTERMEDIATE.retryDelayTicks());
    }

    @Test
    void derivationFirstMatchWins() {
        assertEquals(TraceDifficulty.BASIC, TraceDifficulty.derive(false, false, false, false));
        assertEquals(TraceDifficulty.INTERMEDIATE, TraceDifficulty.derive(false, false, false, true));
        assertEquals(TraceDifficulty.ADVANCED, TraceDifficulty.derive(false, false, true, true));
        assertEquals(TraceDifficulty.DARK, TraceDifficulty.derive(false, true, true, true));
        assertEquals(TraceDifficulty.MASTERY, TraceDifficulty.derive(true, true, true, true));
    }

    @Test
    void wireOrdinalIsClamped() {
        assertEquals(TraceDifficulty.BASIC, TraceDifficulty.byOrdinal(-4));
        assertEquals(TraceDifficulty.MASTERY, TraceDifficulty.byOrdinal(99));
    }
}
