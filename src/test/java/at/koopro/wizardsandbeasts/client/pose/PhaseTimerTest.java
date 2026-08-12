package at.koopro.wizardsandbeasts.client.pose;

import at.koopro.wizardsandbeasts.pose.FlightPoseState;
import at.koopro.wizardsandbeasts.pose.PoseOverride;
import com.mojang.serialization.JsonOps;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/** The timer's ramp and interpolation, and the override's round trip. */
class PhaseTimerTest {

    @Test
    void rampsUpWhileHeldAndBackDownWhenReleased() {
        PhaseTimer timer = new PhaseTimer(4);
        assertTrue(timer.idle());

        for (int i = 0; i < 4; i++) timer.tick(true);
        assertTrue(timer.complete());
        assertEquals(1.0f, timer.value(1f), 1e-6);

        for (int i = 0; i < 4; i++) timer.tick(false);
        assertEquals(0.0f, timer.value(1f), 1e-6, "wound down after four ticks");

        // idle() is deliberately one tick behind: it means "nothing left to interpolate", and on the
        // tick the value first reaches zero the previous value is still 1, so a frame drawn at
        // partialTicks 0.5 would render halfway. Treating that frame as idle is what makes a pose
        // pop out of existence instead of easing.
        assertFalse(timer.idle(), "prevValue has not caught up yet");
        assertEquals(0.5f, timer.raw(0.5f), 1e-6, "still interpolating across the last boundary");

        timer.tick(false);
        assertTrue(timer.idle());
        assertEquals(0.0f, timer.raw(0.5f), 1e-6);
    }

    @Test
    void doesNotOvershootEitherEnd() {
        PhaseTimer timer = new PhaseTimer(2);
        for (int i = 0; i < 20; i++) timer.tick(true);
        assertEquals(2, timer.rawValue());
        for (int i = 0; i < 20; i++) timer.tick(false);
        assertEquals(0, timer.rawValue());
    }

    /**
     * The partial tick interpolates across the boundary rather than snapping.
     *
     * <p>This is the whole reason {@code prevValue} is retained: a pose driven straight off an int
     * that changes twenty times a second steps visibly at any frame rate above twenty.
     */
    @Test
    void interpolatesAcrossTheTickBoundary() {
        PhaseTimer timer = new PhaseTimer(10);
        timer.tick(true);
        timer.tick(true);
        assertEquals(1.0f, timer.raw(0f), 1e-6, "at the start of the frame it is still last tick's value");
        assertEquals(1.5f, timer.raw(0.5f), 1e-6);
        assertEquals(2.0f, timer.raw(1f), 1e-6);
    }

    @Test
    void setSnapsBothEndsSoThereIsNoInterpolationAcrossTheJump() {
        PhaseTimer timer = new PhaseTimer(10);
        timer.set(7);
        assertEquals(7f, timer.raw(0f), 1e-6);
        assertEquals(7f, timer.raw(1f), 1e-6);
    }

    /** One timer sequences several phases; that is what saves keeping three timers in step. */
    @Test
    void betweenRemapsASubRange() {
        assertEquals(0.0f, PhaseTimer.between(8f, 8f, 20f), 1e-6);
        assertEquals(0.5f, PhaseTimer.between(14f, 8f, 20f), 1e-6);
        assertEquals(1.0f, PhaseTimer.between(20f, 8f, 20f), 1e-6);
        assertEquals(0.0f, PhaseTimer.between(3f, 8f, 20f), 1e-6, "before the window");
        assertEquals(1.0f, PhaseTimer.between(99f, 8f, 20f), 1e-6, "after the window");
    }

    @Test
    void betweenHandlesAZeroWidthWindowWithoutDividingByZero() {
        assertEquals(1.0f, PhaseTimer.between(5f, 5f, 5f), 1e-6);
        assertEquals(0.0f, PhaseTimer.between(4f, 5f, 5f), 1e-6);
    }

    @Test
    void rejectsAnEmptyRange() {
        assertThrows(IllegalArgumentException.class, () -> new PhaseTimer(3, 3));
    }

    @Test
    void overrideRoundTripsThroughItsCodec() {
        PoseOverride forced = PoseOverride.forced(FlightPoseState.GLIDE);
        var encoded = PoseOverride.CODEC.encodeStart(JsonOps.INSTANCE, forced)
                .getOrThrow(err -> new AssertionError(err));
        PoseOverride decoded = PoseOverride.CODEC.parse(JsonOps.INSTANCE, encoded)
                .getOrThrow(err -> new AssertionError(err));
        assertEquals(forced, decoded);
    }

    /** An absent state is the resting value, and it must survive a round trip as absent. */
    @Test
    void inactiveOverrideRoundTrips() {
        var encoded = PoseOverride.CODEC.encodeStart(JsonOps.INSTANCE, PoseOverride.NONE)
                .getOrThrow(err -> new AssertionError(err));
        PoseOverride decoded = PoseOverride.CODEC.parse(JsonOps.INSTANCE, encoded)
                .getOrThrow(err -> new AssertionError(err));
        assertEquals(Optional.empty(), decoded.state());
        assertFalse(decoded.active());
    }

    /** The serialized names are the save format; reordering the enum must not change them. */
    @Test
    void stateSerializedNamesAreStable() {
        assertEquals("hover", FlightPoseState.HOVER.getSerializedName());
        assertEquals("glide", FlightPoseState.GLIDE.getSerializedName());
        assertEquals("propelled", FlightPoseState.PROPELLED.getSerializedName());
    }
}
