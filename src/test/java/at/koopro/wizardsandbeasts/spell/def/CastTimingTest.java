package at.koopro.wizardsandbeasts.spell.def;

import at.koopro.wizardsandbeasts.client.pose.ClientCastAnimationState;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Cast timing: the declared data, its guards, and the phase arithmetic that reads it. */
class CastTimingTest {

    @Test
    void roundTripsThroughItsCodec() {
        var timing = new SpellDefinition.CastTiming(24, 0.4f, 0.55f);
        var encoded = SpellDefinition.CastTiming.CODEC.encodeStart(JsonOps.INSTANCE, timing)
                .getOrThrow(err -> new AssertionError(err));
        var decoded = SpellDefinition.CastTiming.CODEC.parse(JsonOps.INSTANCE, encoded)
                .getOrThrow(err -> new AssertionError(err));
        assertEquals(timing, decoded);
    }

    /** Only the duration is required; the phase split has a usable default. */
    @Test
    void phaseBoundsAreOptional() {
        var json = JsonParser.parseString("{\"ticks\": 20}");
        var decoded = SpellDefinition.CastTiming.CODEC.parse(JsonOps.INSTANCE, json)
                .getOrThrow(err -> new AssertionError(err));
        assertEquals(20, decoded.ticks());
        assertTrue(decoded.windupEnd() > 0f && decoded.windupEnd() < decoded.releaseEnd());
    }

    /**
     * Boundaries, not durations.
     *
     * <p>Three durations can disagree about their own total; two boundaries cannot. These guards are
     * what make that guarantee real rather than a comment.
     */
    @Test
    void rejectsPhasesThatCannotBeOrdered() {
        assertThrows(IllegalArgumentException.class,
                () -> new SpellDefinition.CastTiming(20, 0.7f, 0.3f), "windup after release");
        assertThrows(IllegalArgumentException.class,
                () -> new SpellDefinition.CastTiming(20, -0.1f, 0.5f), "negative windup");
        assertThrows(IllegalArgumentException.class,
                () -> new SpellDefinition.CastTiming(20, 0.3f, 1.4f), "release past the end");
    }

    /** A zero-tick cast is instant, and instant is expressed by omitting the field entirely. */
    @Test
    void rejectsANonPositiveDuration() {
        assertThrows(IllegalArgumentException.class, () -> new SpellDefinition.CastTiming(0, 0.3f, 0.6f));
        assertThrows(IllegalArgumentException.class, () -> new SpellDefinition.CastTiming(-5, 0.3f, 0.6f));
    }

    /** The three phases tile the whole cast: exactly one is mid-ramp at any progress. */
    @Test
    void phasesTileTheCastWithNoGap() {
        var cast = new ClientCastAnimationState.ActiveCast("test", 20, 0.35f, 0.6f, 0L);

        assertEquals(0f, cast.windup(0f), 1e-5f);
        assertEquals(1f, cast.windup(0.35f), 1e-5f);
        assertEquals(0f, cast.release(0.35f), 1e-5f);
        assertEquals(1f, cast.release(0.6f), 1e-5f);
        assertEquals(0f, cast.recovery(0.6f), 1e-5f);
        assertEquals(1f, cast.recovery(1f), 1e-5f);

        // Past its own window a phase reads as complete, not as zero — that is what lets a pass hold
        // the last frame of a finished phase instead of snapping back to neutral.
        assertEquals(1f, cast.windup(0.9f), 1e-5f);
    }

    @Test
    void progressRunsZeroToOneAcrossTheDeclaredDuration() {
        var cast = new ClientCastAnimationState.ActiveCast("test", 20, 0.35f, 0.6f, 100L);
        assertEquals(0f, cast.progress(100L, 0f), 1e-5f);
        assertEquals(0.5f, cast.progress(110L, 0f), 1e-5f);
        assertEquals(1f, cast.progress(120L, 0f), 1e-5f);
        assertEquals(1f, cast.progress(200L, 0f), 1e-5f, "clamped, not run past the end");
    }

    /** The partial tick interpolates, or a 20 tick cast steps twenty times however fast the frames run. */
    @Test
    void progressInterpolatesAcrossTheTickBoundary() {
        var cast = new ClientCastAnimationState.ActiveCast("test", 20, 0.35f, 0.6f, 0L);
        assertEquals(0.525f, cast.progress(10L, 0.5f), 1e-5f);
    }

    @Test
    void expiresAtTheEndOfItsDuration() {
        var cast = new ClientCastAnimationState.ActiveCast("test", 20, 0.35f, 0.6f, 0L);
        assertFalse(cast.expired(19L));
        assertTrue(cast.expired(20L));
    }
}
