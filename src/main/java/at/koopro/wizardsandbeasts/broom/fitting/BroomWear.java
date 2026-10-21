package at.koopro.wizardsandbeasts.broom.fitting;

import org.jspecify.annotations.NullMarked;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;

/**
 * What a broom's condition does to how it flies.
 *
 * <p>Before this a broom at three points of durability flew exactly like a new one, right up to the knock that broke
 * it, so polish and servicing only ever mattered at zero. Now a neglected broom starts to wander and lean before it
 * fails, which is the reason to keep one in order. Bands rather than a slope: a characteristic that drifts a
 * fraction every impact cannot be felt, and the flight code would recompute the broom on every knock.
 *
 * <p>Wear never stops a broom flying and never touches the crash numbers — a worn broom is harder to fly, not more
 * dangerous to fall off.
 */
@NullMarked
public enum BroomWear {
    /** Half its durability or more: flies as built. */
    SOUND(Map.of()),
    /** Under half: the bundle has loosened and it no longer holds a line as well. */
    WORN(mods(FittingStat.DRIFT, 1.50f, FittingStat.STABILITY, 0.90f)),
    /** Under a fifth: wanders, leans, and has lost its edge. */
    BATTERED(mods(FittingStat.DRIFT, 2.20f, FittingStat.STABILITY, 0.80f,
            FittingStat.MAX_SPEED, 0.93f, FittingStat.ACCELERATION, 0.90f));

    /** Fraction of durability at or above which a broom is {@link #SOUND}. */
    public static final float SOUND_FROM = 0.50f;
    /** Fraction of durability at or above which a broom is at worst {@link #WORN}. */
    public static final float WORN_FROM = 0.20f;

    private final Map<FittingStat, Float> modifiers;

    BroomWear(Map<FittingStat, Float> modifiers) {
        this.modifiers = modifiers;
    }

    public Map<FittingStat, Float> modifiers() {
        return modifiers;
    }

    public float multiplier(FittingStat stat) {
        return modifiers.getOrDefault(stat, 1.0f);
    }

    public String translationKey() {
        return "broom_wear.wizards_and_beasts." + name().toLowerCase(Locale.ROOT);
    }

    /**
     * The band for {@code remaining} of {@code max} durability. A broom with no durability scale (max ≤ 0) is
     * sound: there is nothing to have worn.
     */
    public static BroomWear of(int remaining, int max) {
        if (max <= 0) {
            return SOUND;
        }
        float fraction = remaining / (float) max;
        if (fraction >= SOUND_FROM) {
            return SOUND;
        }
        return fraction >= WORN_FROM ? WORN : BATTERED;
    }

    private static Map<FittingStat, Float> mods(Object... pairs) {
        EnumMap<FittingStat, Float> map = new EnumMap<>(FittingStat.class);
        for (int i = 0; i < pairs.length; i += 2) {
            map.put((FittingStat) pairs[i], (Float) pairs[i + 1]);
        }
        return Collections.unmodifiableMap(map);
    }
}
