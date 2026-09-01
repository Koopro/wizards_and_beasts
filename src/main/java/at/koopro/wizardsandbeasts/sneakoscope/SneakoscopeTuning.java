package at.koopro.wizardsandbeasts.sneakoscope;

/**
 * Every number the Sneakoscope is made of, in one place and with no game attached.
 *
 * <p>The item's feel is almost entirely arithmetic: how fast the top turns, how often it whirrs,
 * how loudly, how wide it can see, which of sixteen compass sectors it points at. Splitting that
 * arithmetic out from {@code SneakoscopeItem} means the spin can be tuned — and regression-tested —
 * without a level, a player or a render context, the same way {@code ScreenShakeMath} and
 * {@code BroomCameraMath} are.
 *
 * <p><b>Deliberately dependency-free.</b> No Minecraft types, and in particular no
 * {@code Config} read: a pure class that touches the config spec cannot be loaded in a unit test
 * JVM, and a tuning table that cannot be tested is exactly the kind that drifts.
 */
public final class SneakoscopeTuning {

    /** Ticks between scans while idling. Eight is fast enough to feel live, cheap enough to leave on. */
    public static final int BASE_SCAN_INTERVAL_TICKS = 8;
    /** Ticks between scans in focused mode — double the rate, half the reach. */
    public static final int FOCUSED_SCAN_INTERVAL_TICKS = BASE_SCAN_INTERVAL_TICKS / 2;

    /** How far the glass reaches, in blocks. */
    public static final double BASE_SCAN_RADIUS = 12.0;
    /** Focused mode trades reach for refresh rate and a bearing. */
    public static final double FOCUSED_SCAN_RADIUS = BASE_SCAN_RADIUS / 2.0;

    /** Ticks the item is unusable for once focused mode is switched off. */
    public static final int FOCUS_COOLDOWN_TICKS = 40;

    /** Compass sectors the bearing is rounded to: sixteen, i.e. 22.5° each. */
    public static final int BEARING_SECTORS = 16;
    /** Sector value meaning "nothing to point at". */
    public static final int NO_BEARING = -1;
    /** Degrees covered by one sector. */
    public static final float SECTOR_DEGREES = 360.0f / BEARING_SECTORS;

    /** Distinct spin frames the item model dispatches over. */
    public static final int SPIN_FRAMES = 8;

    /** Threat counts at which each band starts. */
    public static final int LOW_THRESHOLD = 1;
    public static final int MEDIUM_THRESHOLD = 3;
    public static final int HIGH_THRESHOLD = 6;

    /** Ceiling on the holder-only alarm tint, so a crowded room never blinds anyone. */
    public static final float MAX_ALARM_ALPHA = 0.28f;

    /** Threats above {@link #HIGH_THRESHOLD} at which the alarm stops getting worse. */
    private static final int ALARM_SATURATION = 4;

    /** Turns per second, indexed by tier ordinal. */
    private static final float[] REVOLUTIONS_PER_SECOND = {0.0f, 0.35f, 1.10f, 2.40f};
    /** Ticks between whirrs, indexed by tier ordinal. Zero means silent. */
    private static final int[] WHIRR_INTERVAL_TICKS = {0, 24, 12, 4};
    /** Whirr gain, indexed by tier ordinal. */
    private static final float[] WHIRR_VOLUME = {0.0f, 0.22f, 0.45f, 0.75f};
    /** Whirr pitch, indexed by tier ordinal — the shriek is the same sample wound up. */
    private static final float[] WHIRR_PITCH = {1.0f, 1.15f, 1.55f, 2.00f};
    /** Silver motes drawn per emission, indexed by tier ordinal. */
    private static final int[] ORBIT_PARTICLES = {0, 0, 2, 4};

    /** Focused mode winds the top up on top of whatever the threats are doing. */
    private static final float FOCUSED_SPIN_MULTIPLIER = 1.5f;

    /**
     * How far the phase wanders off a smooth turn at {@link SneakoscopeTier#LOW}, in revolutions.
     *
     * <p>This is the "slight vibration": at one or two threats the top is not really spinning yet,
     * it is *shivering*, and a smooth slow rotation reads as a decorative idle animation instead of
     * as a warning. Sized to be a little under one model frame so it stutters across a frame
     * boundary now and then rather than every wobble.
     */
    private static final float LOW_WOBBLE_REVOLUTIONS = 0.06f;
    /** Wobble frequency, in Hz. Fast enough to read as a buzz, slow enough not to strobe. */
    private static final float LOW_WOBBLE_HZ = 6.0f;

    /** Alarm pulse frequency, in Hz. One and a bit beats a second — a heartbeat, not a strobe. */
    private static final float ALARM_PULSE_HZ = 1.2f;
    /** Floor of the alarm pulse, so the red never blinks fully out between beats. */
    private static final float ALARM_BASE_ALPHA = 0.08f;

    private SneakoscopeTuning() {}

    /** The band a raw threat count falls into. */
    public static SneakoscopeTier tier(int threats) {
        if (threats >= HIGH_THRESHOLD) {
            return SneakoscopeTier.HIGH;
        }
        if (threats >= MEDIUM_THRESHOLD) {
            return SneakoscopeTier.MEDIUM;
        }
        if (threats >= LOW_THRESHOLD) {
            return SneakoscopeTier.LOW;
        }
        return SneakoscopeTier.CALM;
    }

    /** Ticks between scans. Focused mode doubles the rate. */
    public static int scanIntervalTicks(boolean focused) {
        return focused ? FOCUSED_SCAN_INTERVAL_TICKS : BASE_SCAN_INTERVAL_TICKS;
    }

    /** Scan reach in blocks. Focused mode halves it. */
    public static double scanRadius(boolean focused) {
        return focused ? FOCUSED_SCAN_RADIUS : BASE_SCAN_RADIUS;
    }

    /** Ticks between whirrs, or {@code 0} when the tier is silent. */
    public static int whirrIntervalTicks(SneakoscopeTier tier) {
        return WHIRR_INTERVAL_TICKS[tier.ordinal()];
    }

    public static float whirrVolume(SneakoscopeTier tier) {
        return WHIRR_VOLUME[tier.ordinal()];
    }

    public static float whirrPitch(SneakoscopeTier tier) {
        return WHIRR_PITCH[tier.ordinal()];
    }

    /** Silver motes to seed per emission. Zero below {@link SneakoscopeTier#MEDIUM}. */
    public static int orbitParticles(SneakoscopeTier tier) {
        return ORBIT_PARTICLES[tier.ordinal()];
    }

    /** Turns per second at this tier, with the focused-mode wind-up applied. */
    public static float revolutionsPerSecond(SneakoscopeTier tier, boolean focused) {
        float base = REVOLUTIONS_PER_SECOND[tier.ordinal()];
        return focused ? base * FOCUSED_SPIN_MULTIPLIER : base;
    }

    /**
     * Where in its turn the top is at {@code elapsedMillis}, as a fraction of one revolution in
     * {@code [0, 1)}.
     *
     * <p>At {@link SneakoscopeTier#CALM} this is a constant zero: a Sneakoscope with nothing to
     * report does not move at all, which is the entire point of the object.
     */
    public static float spinPhase(long elapsedMillis, SneakoscopeTier tier, boolean focused) {
        float seconds = elapsedMillis / 1000.0f;
        float phase = seconds * revolutionsPerSecond(tier, focused);
        if (tier == SneakoscopeTier.LOW) {
            phase += LOW_WOBBLE_REVOLUTIONS
                    * (float) Math.sin(seconds * LOW_WOBBLE_HZ * 2.0 * Math.PI);
        }
        return fract(phase);
    }

    /** The model frame {@code [0, SPIN_FRAMES)} a phase lands on. */
    public static int spinFrame(float phase) {
        int frame = (int) (fract(phase) * SPIN_FRAMES);
        return frame >= SPIN_FRAMES ? SPIN_FRAMES - 1 : frame;
    }

    /**
     * Alpha of the holder-only alarm tint in {@code [0, MAX_ALARM_ALPHA]}.
     *
     * <p>Zero below {@link SneakoscopeTier#HIGH}: the screen effect is the thing that says
     * "leave", and spending it on two suspicious villagers would spend it for good.
     */
    public static float alarmPulseAlpha(long elapsedMillis, int threats) {
        if (tier(threats) != SneakoscopeTier.HIGH) {
            return 0.0f;
        }
        int over = Math.min(threats - HIGH_THRESHOLD, ALARM_SATURATION);
        float severity = (float) over / ALARM_SATURATION;
        float seconds = elapsedMillis / 1000.0f;
        float beat = 0.5f + 0.5f * (float) Math.sin(seconds * ALARM_PULSE_HZ * 2.0 * Math.PI);
        float alpha = ALARM_BASE_ALPHA + (MAX_ALARM_ALPHA - ALARM_BASE_ALPHA) * (0.4f + 0.6f * severity) * beat;
        return Math.min(alpha, MAX_ALARM_ALPHA);
    }

    /**
     * The sector {@code [0, BEARING_SECTORS)} a world offset points into, or {@link #NO_BEARING}
     * for an offset with no horizontal direction at all.
     *
     * <p>Sector 0 is {@code +X} and sectors advance towards {@code +Z}. That is deliberately
     * <em>not</em> Minecraft's yaw convention: the value is written on the wire and read back by the
     * client to rebuild a direction vector, so it only has to agree with {@link #sectorDirectionX}
     * and {@link #sectorDirectionZ}, and a self-contained convention cannot be broken by a change
     * to what yaw means.
     */
    public static int bearingSector(double dx, double dz) {
        if (dx == 0.0 && dz == 0.0) {
            return NO_BEARING;
        }
        double degrees = Math.toDegrees(Math.atan2(dz, dx));
        if (degrees < 0.0) {
            degrees += 360.0;
        }
        int sector = (int) Math.round(degrees / SECTOR_DEGREES);
        return sector % BEARING_SECTORS;
    }

    /** Mid-angle of a sector, in degrees. */
    public static float sectorDegrees(int sector) {
        return sector * SECTOR_DEGREES;
    }

    /** {@code x} component of the unit vector a sector points along. */
    public static double sectorDirectionX(int sector) {
        return Math.cos(Math.toRadians(sectorDegrees(sector)));
    }

    /** {@code z} component of the unit vector a sector points along. */
    public static double sectorDirectionZ(int sector) {
        return Math.sin(Math.toRadians(sectorDegrees(sector)));
    }

    /** True when {@code sector} is a real bearing rather than {@link #NO_BEARING}. */
    public static boolean hasBearing(int sector) {
        return sector >= 0 && sector < BEARING_SECTORS;
    }

    private static float fract(float value) {
        float f = value - (float) Math.floor(value);
        // floor() of a large negative float can round back to the value itself; clamp rather than
        // hand a caller a phase of exactly 1.0, which would index one past the last frame.
        return f >= 1.0f ? 0.0f : f;
    }
}
