package at.koopro.wizardsandbeasts.event.pose;

import at.koopro.wizardsandbeasts.pose.FlightPoseState;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** The speed-to-state filter, driven over tick series that reproduce what the server actually sees. */
class FlightStateDeriverTest {

    /** Runs a series of raw per-tick speeds through the filter and reports the states it settles on. */
    private static Set<FlightPoseState> statesAfterWarmup(double[] rawSpeeds, int warmup) {
        Set<FlightPoseState> seen = EnumSet.noneOf(FlightPoseState.class);
        double smoothed = rawSpeeds[0];
        FlightPoseState state = null;
        for (int tick = 0; tick < rawSpeeds.length; tick++) {
            smoothed = FlightStateDeriver.smooth(smoothed, rawSpeeds[tick]);
            state = FlightStateDeriver.classify(smoothed, state);
            if (tick >= warmup) {
                seen.add(state);
            }
        }
        return seen;
    }

    private static double[] alternating(int ticks, double low, double high) {
        double[] speeds = new double[ticks];
        for (int i = 0; i < ticks; i++) {
            speeds[i] = i % 2 == 0 ? low : high;
        }
        return speeds;
    }

    /**
     * The glitch this filter exists for.
     *
     * <p>A player holding a steady 0.4 blocks/tick does not reach the server as 0.4 every tick. The
     * movement arrives in packets, so a tick that receives none reads as stationary and the next
     * reads as double. Classified raw, that is HOVER and PROPELLED alternating twenty times a
     * second, and every flip restarts the client's cross-fade.
     */
    @Test
    void aDroppedMovementPacketDoesNotFlipTheState() {
        Set<FlightPoseState> settled = statesAfterWarmup(alternating(60, 0.0, 0.8), 20);
        assertEquals(Set.of(FlightPoseState.GLIDE), settled,
                "a steady 0.4 average must read as one state, whatever the packets did");
    }

    /** The same series without the filter, to show the smoothing is what fixes it and not luck. */
    @Test
    void thatSameSeriesClassifiedRawIsWhatWasGlitching() {
        Set<FlightPoseState> raw = EnumSet.noneOf(FlightPoseState.class);
        FlightPoseState state = null;
        for (double speed : alternating(60, 0.0, 0.8)) {
            state = FlightStateDeriver.classify(speed, state);
            raw.add(state);
        }
        assertTrue(raw.size() > 1, "unsmoothed input should flap — that is the bug being fixed");
    }

    /** Hysteresis handles the rest: a steady speed parked on a threshold must still pick one side. */
    @Test
    void aSpeedSittingExactlyOnAThresholdDoesNotOscillate() {
        double[] steady = new double[60];
        java.util.Arrays.fill(steady, 0.12);
        assertEquals(1, statesAfterWarmup(steady, 20).size(),
                "a constant speed must settle on exactly one state");
    }

    @Test
    void classifiesTheThreeBands() {
        assertEquals(FlightPoseState.HOVER, FlightStateDeriver.classify(0.0, null));
        assertEquals(FlightPoseState.GLIDE, FlightStateDeriver.classify(0.3, null));
        assertEquals(FlightPoseState.PROPELLED, FlightStateDeriver.classify(1.0, null));
    }

    /** Falling back must cross a lower bar than rising did, or the boundary flaps. */
    @Test
    void thresholdsAreAsymmetricByState() {
        double justUnderPropelled = 0.5;
        assertEquals(FlightPoseState.PROPELLED,
                FlightStateDeriver.classify(justUnderPropelled, FlightPoseState.PROPELLED),
                "already propelled: hold it");
        assertEquals(FlightPoseState.GLIDE,
                FlightStateDeriver.classify(justUnderPropelled, FlightPoseState.GLIDE),
                "not yet propelled: the same speed is not enough to get there");
    }

    /** Accelerating from rest must pass through glide rather than jumping straight to propelled. */
    @Test
    void accelerationPassesThroughEveryState() {
        Set<FlightPoseState> seen = EnumSet.noneOf(FlightPoseState.class);
        double smoothed = 0;
        FlightPoseState state = null;
        for (int tick = 0; tick < 60; tick++) {
            smoothed = FlightStateDeriver.smooth(smoothed, Math.min(1.0, tick * 0.02));
            state = FlightStateDeriver.classify(smoothed, state);
            seen.add(state);
        }
        assertEquals(EnumSet.allOf(FlightPoseState.class), seen);
    }

    /** The filter must converge on a held speed, not creep or ring. */
    @Test
    void smoothingConvergesOnASteadyReading() {
        double smoothed = 0;
        for (int i = 0; i < 50; i++) {
            smoothed = FlightStateDeriver.smooth(smoothed, 0.42);
        }
        assertEquals(0.42, smoothed, 1e-3);
    }
}
