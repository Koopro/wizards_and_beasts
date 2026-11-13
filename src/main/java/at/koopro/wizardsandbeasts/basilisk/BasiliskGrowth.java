package at.koopro.wizardsandbeasts.basilisk;

import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;

/**
 * How big a basilisk is at a given age. Pure numbers; {@code BasiliskEntity} applies them.
 *
 * <p>The owner's rule (2026-11-06): a basilisk hatches small and keeps growing, fast at first and then slowly, up to a
 * cap. Its size multiplies its body (model and hitbox, through {@code Attributes.SCALE}), its health and its bite; its
 * gaze kills at every size, as in canon.
 *
 * <ul>
 *   <li>Hatchling: {@link #HATCHLING} of the adult size, about two blocks long.</li>
 *   <li>Adult (today's size, about eleven blocks long) at {@link #ADULT_TICKS}, five in-game days.</li>
 *   <li>Cap: {@link #CAP} times the adult, about twenty-two blocks, at {@link #CAP_TICKS}, fifty days. The Chamber of
 *       Secrets' basilisk, a thousand years old, wakes at the cap.</li>
 * </ul>
 * Both stretches ease out: most of each step happens early in it.
 */
public final class BasiliskGrowth {

    private BasiliskGrowth() {}

    public static final long DAY = 24_000L;
    public static final float HATCHLING = 0.18f;
    public static final long ADULT_TICKS = 5 * DAY;
    public static final float CAP = 2.0f;
    public static final long CAP_TICKS = 50 * DAY;

    /** Size relative to an adult (1.0) for an age in ticks. */
    public static float size(long ageTicks) {
        if (ageTicks <= 0) {
            return HATCHLING;
        }
        if (ageTicks < ADULT_TICKS) {
            return HATCHLING + (1f - HATCHLING) * easeOut(ageTicks / (float) ADULT_TICKS);
        }
        if (ageTicks < CAP_TICKS) {
            return 1f + (CAP - 1f) * easeOut((ageTicks - ADULT_TICKS) / (float) (CAP_TICKS - ADULT_TICKS));
        }
        return CAP;
    }

    /** A wild basilisk is met grown: somewhere between adult and three times that age. */
    public static long wildAge(RandomSource random) {
        return ADULT_TICKS + (long) (random.nextFloat() * 2 * ADULT_TICKS);
    }

    static float easeOut(float t) {
        float u = 1f - Mth.clamp(t, 0f, 1f);
        return 1f - u * u;
    }
}
