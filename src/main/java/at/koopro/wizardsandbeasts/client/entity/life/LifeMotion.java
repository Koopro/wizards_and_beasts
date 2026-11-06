package at.koopro.wizardsandbeasts.client.entity.life;

import net.minecraft.util.Mth;

/**
 * The pure numbers behind {@link CreatureLifePass}: how far a head turns, when a lid closes, how a chest
 * breathes and when an ear flicks. Everything is a function of the creature's age in ticks (partial-tick
 * continuous) and a per-entity seed, so two creatures side by side never blink or breathe in step, and the
 * same creature looks the same on every client without any of it being synced.
 */
public final class LifeMotion {

    private LifeMotion() {}

    /** The furthest a head (plus neck) turns away from the body, in degrees. Vanilla mobs allow ~75. */
    public static final float MAX_YAW = 70f;
    /** The furthest a head (plus neck) tips up or down, in degrees. */
    public static final float MAX_PITCH = 40f;
    /** Share of the turn the neck chain takes when the rig has one; the head takes the rest. */
    public static final float NECK_SHARE = 0.5f;

    /** Lid fully closes and opens again over this many ticks: 2 down, 1 shut, 2 up. */
    static final float BLINK_TICKS = 5f;

    /**
     * Fraction of the whole turn each neck bone takes, and the head takes {@code 1 - neckCount * share}.
     *
     * @return the per-neck-bone share; the head's share is {@link #headShare(int)}
     */
    public static float neckShare(int neckCount) {
        return neckCount <= 0 ? 0f : NECK_SHARE / neckCount;
    }

    public static float headShare(int neckCount) {
        return neckCount <= 0 ? 1f : 1f - NECK_SHARE;
    }

    /** Clamps a look angle to {@code ±limit}, wrapping first so a 350° yaw reads as -10°. */
    public static float clampLook(float degrees, float limit) {
        return Mth.clamp(Mth.wrapDegrees(degrees), -limit, limit);
    }

    /** Seconds-scale gap between blinks for this seed: 3 to 7.5 s, fixed per creature. */
    static float blinkPeriod(long seed) {
        return 60f + Math.floorMod(seed * 0x9E3779B97F4A7C15L >>> 40, 91L);
    }

    /**
     * How shut the lids are, 0 open to 1 closed. A short close-hold-open pulse once per {@link #blinkPeriod};
     * every third blink is a double blink, which is what keeps a slow period from reading as a metronome.
     */
    public static float blinkClosure(float ageTicks, long seed) {
        float period = blinkPeriod(seed);
        float offset = Math.floorMod(seed, (long) period);
        float t = ageTicks + offset;
        long cycle = (long) Math.floor(t / period);
        float local = t - cycle * period;
        float closure = pulse(local);
        if (Math.floorMod(cycle + seed, 3L) == 0) {
            closure = Math.max(closure, pulse(local - (BLINK_TICKS + 2f)));
        }
        return closure;
    }

    /** One blink pulse starting at {@code local = 0}: linear down over 2 ticks, 1 tick shut, up over 2. */
    static float pulse(float local) {
        if (local < 0f || local >= BLINK_TICKS) return 0f;
        if (local < 2f) return local / 2f;
        if (local < 3f) return 1f;
        return 1f - (local - 3f) / 2f;
    }

    /**
     * Breath period in ticks. Bigger animals breathe slower (roughly with the square root of their height),
     * sleeping animals slower still.
     */
    public static float breathPeriod(float heightBlocks, boolean asleep) {
        float awake = 30f * (float) Math.sqrt(Math.max(0.25f, heightBlocks));
        return asleep ? awake * 1.7f : awake;
    }

    /** Chest scale for this tick: {@code 1 + amplitude * sin}, phase-shifted per creature. */
    public static float breathScale(float ageTicks, float periodTicks, boolean asleep, long seed) {
        float amplitude = asleep ? 0.035f : 0.018f;
        float phase = (Math.floorMod(seed, 1000L) / 1000f) * Mth.TWO_PI;
        return 1f + amplitude * Mth.sin(ageTicks / periodTicks * Mth.TWO_PI + phase);
    }

    /**
     * Ear flick angle in radians, positive for the left ear and negative for the right one so both flick
     * outward. Every 4.5 to 10 s one ear (alternating) flicks for 6 ticks.
     *
     * @return {@code {left, right}}
     */
    public static float[] earFlick(float ageTicks, long seed) {
        float period = 90f + Math.floorMod(seed * 31L + 7L, 111L);
        float t = ageTicks + Math.floorMod(seed * 17L, (long) period);
        long cycle = (long) Math.floor(t / period);
        float local = t - cycle * period;
        float amount = local < 6f ? Mth.sin(local / 6f * Mth.PI) * 15f * Mth.DEG_TO_RAD : 0f;
        boolean left = Math.floorMod(cycle, 2L) == 0;
        return left ? new float[] {amount, 0f} : new float[] {0f, -amount};
    }

    /** Moves {@code current} toward {@code target} by at most {@code step}. */
    public static float approach(float current, float target, float step) {
        if (current < target) return Math.min(target, current + step);
        return Math.max(target, current - step);
    }
}
