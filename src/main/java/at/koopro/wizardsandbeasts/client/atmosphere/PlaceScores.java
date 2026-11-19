package at.koopro.wizardsandbeasts.client.atmosphere;

import org.jspecify.annotations.NullMarked;

/**
 * How strongly the camera's surroundings read as one kind of place, from a stream of noisy samples.
 *
 * <p>Each tick {@link PlaceSense} looks at a handful of random blocks and hands this a frame value: the weighted share
 * of those blocks that belong to the place (plus a constant if the biome does). One frame is noise; the score is its
 * exponential average, so a room is recognised after a couple of seconds and forgotten a couple of seconds after
 * leaving it.
 *
 * <p>Hysteresis keeps a doorway from flickering: the place switches on above {@code enter}, off only below
 * {@code leave}, and never sooner than {@link #MIN_DWELL} ticks after it last switched. {@link #strength} is what the
 * effects use: 0 when off, rising with the score to 1 at {@code full}, and eased so mist and sound fade in and out
 * instead of popping.
 *
 * <p>Pure: no Minecraft types, unit-tested.
 */
@NullMarked
public final class PlaceScores {

    /** Ticks between switches at least: three seconds. */
    static final int MIN_DWELL = 60;
    /** How far a frame moves the score: a half-life of about four seconds at 20 ticks. */
    static final double ALPHA = 1.0 - Math.pow(0.5, 1.0 / 80.0);
    /** How far strength moves toward its target per tick: a full fade in three seconds. */
    static final float EASE = 1f / 60f;

    private final double enter;
    private final double leave;
    private final double full;

    private double score;
    private boolean active;
    private int sinceSwitch = MIN_DWELL;
    private float strength;

    /**
     * @param enter score above which the place starts
     * @param leave score below which it stops; below {@code enter}
     * @param full  score at which it shows at full strength; at least {@code enter}
     */
    public PlaceScores(double enter, double leave, double full) {
        if (!(leave < enter) || full < enter || leave < 0) {
            throw new IllegalArgumentException("need 0 <= leave < enter <= full, got " + leave + ", " + enter + ", " + full);
        }
        this.enter = enter;
        this.leave = leave;
        this.full = full;
    }

    /** One tick: fold in this frame's value, switch if the thresholds say so, ease the strength. */
    public void observe(double frame) {
        score += ALPHA * (frame - score);
        sinceSwitch++;
        if (sinceSwitch >= MIN_DWELL) {
            if (!active && score > enter) {
                active = true;
                sinceSwitch = 0;
            } else if (active && score < leave) {
                active = false;
                sinceSwitch = 0;
            }
        }
        float target = active ? (float) Math.min(1.0, Math.max(0.0, (score - leave) / (full - leave))) : 0f;
        if (strength < target) {
            strength = Math.min(target, strength + EASE);
        } else if (strength > target) {
            strength = Math.max(target, strength - EASE);
        }
    }

    /** Forget everything at once: a new world, a dimension change, a teleport. */
    public void reset() {
        score = 0;
        active = false;
        sinceSwitch = MIN_DWELL;
        strength = 0f;
    }

    public double score() {
        return score;
    }

    public boolean active() {
        return active;
    }

    /** 0..1, eased: how much of the place's look and sound to show. */
    public float strength() {
        return strength;
    }
}
