package at.koopro.wizardsandbeasts.creature.ecology;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import net.minecraft.util.StringRepresentable;
import org.jspecify.annotations.NullMarked;

import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static at.koopro.wizardsandbeasts.creature.ecology.EcologyCapability.*;

/**
 * One creature's temperament: five axes from 0 to 1, drawn inside its species' range.
 *
 * <p><b>Individual, persistent, free.</b> The value is a hash of the creature's UUID mapped into the range, so the
 * same animal is the same character after a reload or a server restart, and nothing is saved or synced.
 *
 * <p><b>Bounded.</b> Personality only ever <i>scales</i> a threshold of a behaviour the species already has — by
 * {@link #raise} or {@link #lower}, which stay inside 0.8–1.2 — it never adds or removes a capability. A bold
 * unicorn still flees a predator at its throat; a timid one only flees it from further off. A placid hippogriff still
 * defends its ground; it just lets you a little closer first ({@code EcologyPersonalityTest} holds this).
 *
 * <p>A neutral personality (every axis 0.5) scales every threshold by exactly 1, so a creature without variation
 * behaves exactly as the plain policy says.
 */
@NullMarked
public record EcologyPersonality(float boldness, float curiosity, float sociability, float aggression, float activity) {

    public static final EcologyPersonality NEUTRAL = new EcologyPersonality(0.5f, 0.5f, 0.5f, 0.5f, 0.5f);

    /** Smallest and largest factor an axis may put on a threshold. */
    static final float MIN_FACTOR = 0.8f;
    static final float MAX_FACTOR = 1.2f;

    /** A factor that grows with the axis: 0.8 at 0, 1 at 0.5, 1.2 at 1. */
    public float raise(float axis) {
        return MIN_FACTOR + (MAX_FACTOR - MIN_FACTOR) * clamp(axis);
    }

    /** A factor that shrinks with the axis: 1.2 at 0, 1 at 0.5, 0.8 at 1. */
    public float lower(float axis) {
        return MAX_FACTOR - (MAX_FACTOR - MIN_FACTOR) * clamp(axis);
    }

    public float get(Axis axis) {
        return switch (axis) {
            case BOLDNESS -> boldness;
            case CURIOSITY -> curiosity;
            case SOCIABILITY -> sociability;
            case AGGRESSION -> aggression;
            case ACTIVITY -> activity;
        };
    }

    /** The individual a UUID names, inside {@code ranges}. Deterministic: same UUID, same character. */
    public static EcologyPersonality of(UUID id, Map<Axis, Range> ranges) {
        long seed = id.getMostSignificantBits() ^ Long.rotateLeft(id.getLeastSignificantBits(), 31);
        float[] v = new float[Axis.values().length];
        for (Axis axis : Axis.values()) {
            Range range = ranges.getOrDefault(axis, Range.DEFAULT);
            v[axis.ordinal()] = range.at(unit(mix(seed + 0x9E3779B97F4A7C15L * (axis.ordinal() + 1))));
        }
        return new EcologyPersonality(v[0], v[1], v[2], v[3], v[4]);
    }

    /**
     * A species' ranges: what its capabilities imply, then anything its profile states. A prey animal is timid by
     * default, a herd animal sociable, a solitary one not; an aggressive one is bold and quick to anger.
     */
    public static Map<Axis, Range> rangesFor(Set<EcologyCapability> capabilities, Map<Axis, Range> stated) {
        Map<Axis, Range> out = new EnumMap<>(Axis.class);
        if (capabilities.contains(PREY) || capabilities.contains(COWARDLY)) {
            out.put(Axis.BOLDNESS, new Range(0.1f, 0.45f));
        }
        if (capabilities.contains(AGGRESSIVE)) {
            out.put(Axis.BOLDNESS, new Range(0.5f, 0.9f));
            out.put(Axis.AGGRESSION, new Range(0.6f, 0.95f));
        }
        if (capabilities.contains(CURIOUS)) {
            out.put(Axis.CURIOSITY, new Range(0.55f, 0.95f));
        }
        if (capabilities.contains(HERD) || capabilities.contains(SOCIAL)) {
            out.put(Axis.SOCIABILITY, new Range(0.6f, 0.95f));
        }
        if (capabilities.contains(SOLITARY)) {
            out.put(Axis.SOCIABILITY, new Range(0.0f, 0.3f));
        }
        out.putAll(stated);
        return out;
    }

    private static long mix(long z) { // SplitMix64 finaliser
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }

    private static float unit(long bits) {
        return (bits >>> 40) / (float) (1L << 24);
    }

    private static float clamp(float v) {
        return v < 0 ? 0 : Math.min(v, 1);
    }

    /** A personality axis, named in profiles ({@code "personality": {"boldness": [0.2, 0.5]}}). */
    public enum Axis implements StringRepresentable {
        BOLDNESS, CURIOSITY, SOCIABILITY, AGGRESSION, ACTIVITY;

        public static final Codec<Axis> CODEC = StringRepresentable.fromEnum(Axis::values);

        @Override
        public String getSerializedName() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    /** Where in 0..1 a species' individuals fall on one axis. Written {@code [min, max]}. */
    public record Range(float min, float max) {
        public static final Range DEFAULT = new Range(0.3f, 0.7f);

        public static final Codec<Range> CODEC = Codec.floatRange(0, 1).listOf(2, 2).comapFlatMap(
                list -> list.get(0) <= list.get(1)
                        ? DataResult.success(new Range(list.get(0), list.get(1)))
                        : DataResult.error(() -> "personality range " + list + " has min above max"),
                range -> List.of(range.min, range.max));

        float at(float unit) {
            return min + (max - min) * unit;
        }
    }
}
