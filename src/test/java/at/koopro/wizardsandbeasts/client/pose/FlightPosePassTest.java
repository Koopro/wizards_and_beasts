package at.koopro.wizardsandbeasts.client.pose;

import at.koopro.wizardsandbeasts.pose.FlightPoseState;
import at.koopro.wizardsandbeasts.pose.PoseOverride;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The flight pass's per-player state machine, driven without a client.
 *
 * <p>{@code Tracked} is private and stays private — it is an implementation detail of the pass, not
 * an API — so the test reaches it reflectively rather than widening the class's surface for the sake
 * of being tested. The alternative is a package-private type that reads as though something else is
 * meant to use it.
 */
class FlightPosePassTest {

    private final FlightPosePass pass = new FlightPosePass();

    private Object newTracked() {
        try {
            Class<?> type = Class.forName(
                    "at.koopro.wizardsandbeasts.client.pose.FlightPosePass$Tracked");
            Constructor<?> ctor = type.getDeclaredConstructor();
            ctor.setAccessible(true);
            return ctor.newInstance();
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("FlightPosePass.Tracked moved or changed shape", e);
        }
    }

    private void tick(Object tracked, PoseOverride override, boolean airborne, float yaw) {
        try {
            var method = FlightPosePass.class.getDeclaredMethod("tick",
                    Class.forName("at.koopro.wizardsandbeasts.client.pose.FlightPosePass$Tracked"),
                    PoseOverride.class, boolean.class, float.class);
            method.setAccessible(true);
            method.invoke(pass, tracked, override, airborne, yaw);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("FlightPosePass.tick moved or changed shape", e);
        }
    }

    private <T> T read(Object tracked, String field, Class<T> type) {
        try {
            Field f = tracked.getClass().getDeclaredField(field);
            f.setAccessible(true);
            return type.cast(f.get(tracked));
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("field " + field + " moved", e);
        }
    }

    private boolean posing(Object tracked) {
        FadeTimer fade = read(tracked, "fade", FadeTimer.class);
        return read(tracked, "toState", Object.class) != null && !fade.idle();
    }

    private Object flying(FlightPoseState state, int ticks) {
        Object tracked = newTracked();
        for (int i = 0; i < ticks; i++) {
            tick(tracked, PoseOverride.forced(state), true, 0f);
        }
        return tracked;
    }

    /**
     * Landing used to drop the pose on the tick the player touched the ground: the override went
     * absent, the pass cleared its state, and {@code pose} returned early on the null — so the fade
     * wound down with nobody reading it. The state has to outlive the deactivation.
     */
    @Test
    void landingEasesOutInsteadOfSnapping() {
        Object tracked = flying(FlightPoseState.GLIDE, 20);
        assertTrue(posing(tracked));

        tick(tracked, PoseOverride.NONE, false, 0f);
        assertTrue(posing(tracked), "the pose must survive the tick the override went away");
        assertEquals(FlightPoseState.GLIDE, read(tracked, "toState", Object.class),
                "the state is still needed — it is what the fade is fading out of");

        for (int i = 0; i < FlightPoseConstants.FADE_OUT_TICKS + 2; i++) {
            tick(tracked, PoseOverride.NONE, false, 0f);
        }
        assertFalse(posing(tracked), "the fade has run its course");
        assertNull(read(tracked, "toState", Object.class));
    }

    /** Releasing is quicker than committing, so the fade out must finish inside the fade in's span. */
    @Test
    void theFadeOutIsQuickerThanTheFadeIn() {
        Object tracked = flying(FlightPoseState.GLIDE, 40);
        for (int i = 0; i < FlightPoseConstants.FADE_OUT_TICKS; i++) {
            tick(tracked, PoseOverride.NONE, false, 0f);
        }
        assertEquals(0f, read(tracked, "fade", FadeTimer.class).rawValue(), 1e-5f,
                "five ticks should be the whole fade out");
    }

    /**
     * A grounded player with a stored override is not posed.
     *
     * <p>The command accepts one deliberately — schema §8 — so this is the check that "stored" and
     * "rendered" stay different things.
     */
    @Test
    void aStoredOverrideDoesNothingUntilThePlayerLeavesTheGround() {
        Object tracked = newTracked();
        for (int i = 0; i < 20; i++) {
            tick(tracked, PoseOverride.forced(FlightPoseState.GLIDE), false, 0f);
        }
        assertFalse(posing(tracked), "stored, not rendered");

        for (int i = 0; i < 20; i++) {
            tick(tracked, PoseOverride.forced(FlightPoseState.GLIDE), true, 0f);
        }
        assertTrue(posing(tracked), "and it takes effect once airborne");
    }

    /** Switching state mid-flight keeps the pass posing throughout — no gap between the two. */
    @Test
    void switchingStateInFlightNeverStopsPosing() {
        Object tracked = flying(FlightPoseState.HOVER, 20);
        for (int i = 0; i < FlightPoseConstants.STATE_BLEND_TICKS + 2; i++) {
            tick(tracked, PoseOverride.forced(FlightPoseState.PROPELLED), true, 0f);
            assertTrue(posing(tracked), "the cross-fade must not blink");
        }
        assertEquals(FlightPoseState.PROPELLED, read(tracked, "toState", Object.class));
    }

    /** A steady heading must not bank. The first tick seeds the yaw and produces no delta. */
    @Test
    void flyingStraightDoesNotBank() {
        Object tracked = newTracked();
        for (int i = 0; i < 30; i++) {
            tick(tracked, PoseOverride.forced(FlightPoseState.GLIDE), true, 90f);
        }
        assertEquals(0f, read(tracked, "bank", Float.class), 1e-4f);
    }

    /** A sustained turn builds bank, in the direction of the turn, up to the clamp and no further. */
    @Test
    void aSustainedTurnBanksAndRespectsTheClamp() {
        Object tracked = newTracked();
        float yaw = 0f;
        for (int i = 0; i < 60; i++) {
            yaw += 20f;
            tick(tracked, PoseOverride.forced(FlightPoseState.GLIDE), true, yaw);
        }
        float bank = read(tracked, "bank", Float.class);
        assertTrue(bank > 0f, "turning one way should bank that way, got " + bank);
        assertTrue(bank <= FlightPoseConstants.BANK_CLAMP + 1e-4f, "bank exceeded its clamp: " + bank);

        Object other = newTracked();
        yaw = 0f;
        for (int i = 0; i < 60; i++) {
            yaw -= 20f;
            tick(other, PoseOverride.forced(FlightPoseState.GLIDE), true, yaw);
        }
        assertTrue(read(other, "bank", Float.class) < 0f, "the opposite turn must bank the other way");
    }

    /**
     * Yaw is wrapped before it drives the bank.
     *
     * <p>Crossing the 180 degree seam is a 1 degree turn that subtracts to 359. Unwrapped it slams
     * the bank to its clamp every time a player turns through south.
     */
    @Test
    void crossingTheYawSeamIsNotAFullTurn() {
        Object tracked = newTracked();
        tick(tracked, PoseOverride.forced(FlightPoseState.GLIDE), true, 179.5f);
        tick(tracked, PoseOverride.forced(FlightPoseState.GLIDE), true, -179.5f);
        float bank = read(tracked, "bank", Float.class);
        assertTrue(Math.abs(bank) < 1f, "a one degree turn should barely bank, got " + bank);
    }

    /** The bank unwinds when the pose deactivates, rather than freezing at its last angle. */
    @Test
    void bankUnwindsWhenThePoseStops() {
        Object tracked = newTracked();
        float yaw = 0f;
        for (int i = 0; i < 40; i++) {
            yaw += 20f;
            tick(tracked, PoseOverride.forced(FlightPoseState.GLIDE), true, yaw);
        }
        assertTrue(read(tracked, "bank", Float.class) > 1f);

        for (int i = 0; i < 60; i++) {
            tick(tracked, PoseOverride.NONE, false, yaw);
        }
        assertEquals(0f, read(tracked, "bank", Float.class), 0.05f);
    }

    /** Priority has to stay inside the locomotion band or registration rejects the pass. */
    @Test
    void sitsInTheLocomotionBand() {
        assertDoesNotThrow(() -> PoseBand.require(FlightPosePass.PRIORITY, "flight"));
    }
}
