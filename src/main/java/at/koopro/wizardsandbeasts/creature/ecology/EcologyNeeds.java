package at.koopro.wizardsandbeasts.creature.ecology;

import com.mojang.serialization.Codec;
import org.jspecify.annotations.NullMarked;

import java.util.EnumMap;
import java.util.Map;

/**
 * A creature's needs, as pressures from 0 (satisfied) to 1 (desperate). Mutable, one per creature, held in its
 * {@link EcologyState}; {@link EcologyBrain} sees an immutable {@link View}.
 *
 * <p><b>Cost.</b> Seven floats. Needs are not ticked: {@link #advance} is called at each look around (the MEDIUM
 * tier) with the ticks since the last call, so their cost is a handful of float operations a second, and a creature
 * whose chunk was unloaded simply resumes ({@code EcologyState} does not carry the last-advanced tick across a save,
 * so no time is charged for the unloaded span). Only needs the profile declares ever move; the rest stay 0, which
 * {@link EcologyBrain} reads as "has no such need".
 */
@NullMarked
public final class EcologyNeeds {

    /** Resting or asleep, tiredness falls this many times faster than it grows. */
    static final float RECOVERY = 4.0f;

    private final float[] values = new float[EcologyNeed.values().length];

    public float get(EcologyNeed need) {
        return values[need.ordinal()];
    }

    public void set(EcologyNeed need, float value) {
        values[need.ordinal()] = clamp(value);
    }

    /** Brings a need down by {@code amount} (eating, drinking, company). */
    public void satisfy(EcologyNeed need, float amount) {
        set(need, get(need) - amount);
    }

    /** A fright: safety pressure to full, if the creature has the need at all. */
    public void alarm(EcologyProfile profile) {
        if (profile.needs().containsKey(EcologyNeed.SAFETY)) {
            set(EcologyNeed.SAFETY, 1.0f);
        }
    }

    /**
     * Moves every declared need on by {@code elapsed} ticks. Pure arithmetic on this object.
     *
     * @param resting  resting or asleep: tiredness recovers instead of growing
     * @param withKin  its own kind close by: loneliness falls instead of growing
     */
    public void advance(EcologyProfile profile, EcologyPersonality personality, long elapsed, boolean resting,
                        boolean withKin) {
        if (elapsed <= 0) {
            return;
        }
        for (Map.Entry<EcologyNeed, Integer> e : profile.needs().entrySet()) {
            EcologyNeed need = e.getKey();
            float ticks = Math.max(1, e.getValue());
            float step = elapsed / ticks;
            float v = get(need);
            switch (need) {
                case HUNGER, THIRST, MAGIC -> v += step;
                // A curious individual tires of sameness sooner; an energetic one tires later.
                case CURIOSITY -> v += step / personality.lower(personality.curiosity());
                case ENERGY -> v = resting ? v - step * RECOVERY : v + step / personality.raise(personality.activity());
                case SOCIAL -> v = withKin ? v - step * RECOVERY : v + step / personality.lower(personality.sociability());
                case SAFETY -> v -= step; // a fright fades
            }
            set(need, v);
        }
    }

    public View view() {
        return new View(values[0], values[1], values[2], values[3], values[4], values[5], values[6]);
    }

    /** The non-zero needs, for saving. */
    Map<EcologyNeed, Float> saved() {
        Map<EcologyNeed, Float> out = new EnumMap<>(EcologyNeed.class);
        for (EcologyNeed need : EcologyNeed.values()) {
            if (get(need) > 0) {
                out.put(need, get(need));
            }
        }
        return out;
    }

    void load(Map<EcologyNeed, Float> saved) {
        saved.forEach(this::set);
    }

    static final Codec<Map<EcologyNeed, Float>> SAVED_CODEC = Codec.unboundedMap(EcologyNeed.CODEC, Codec.floatRange(0, 1));

    private static float clamp(float v) {
        return v < 0 ? 0 : Math.min(v, 1);
    }

    /** What the brain sees: every need's pressure at the last look. */
    public record View(float hunger, float thirst, float energy, float social, float curiosity, float safety,
                       float magic) {
        public static final View NONE = new View(0, 0, 0, 0, 0, 0, 0);

        public float get(EcologyNeed need) {
            return switch (need) {
                case HUNGER -> hunger;
                case THIRST -> thirst;
                case ENERGY -> energy;
                case SOCIAL -> social;
                case CURIOSITY -> curiosity;
                case SAFETY -> safety;
                case MAGIC -> magic;
            };
        }

        public View with(EcologyNeed need, float value) {
            return new View(
                    need == EcologyNeed.HUNGER ? value : hunger,
                    need == EcologyNeed.THIRST ? value : thirst,
                    need == EcologyNeed.ENERGY ? value : energy,
                    need == EcologyNeed.SOCIAL ? value : social,
                    need == EcologyNeed.CURIOSITY ? value : curiosity,
                    need == EcologyNeed.SAFETY ? value : safety,
                    need == EcologyNeed.MAGIC ? value : magic);
        }
    }
}
