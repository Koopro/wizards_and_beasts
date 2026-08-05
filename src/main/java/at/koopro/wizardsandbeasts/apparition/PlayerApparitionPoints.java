package at.koopro.wizardsandbeasts.apparition;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * The set of destinations one player has memorised. Persisted per player and carried across death — a
 * wizard does not forget the places they know because they died.
 *
 * @param points saved destinations, in the order they were marked
 */
@NullMarked
public record PlayerApparitionPoints(List<ApparitionPoint> points) {

    /**
     * Absolute ceiling on saved destinations, so the list stays a menu rather than a database. The cap a
     * given wizard is actually held to is lower and grows with practice — see
     * {@code ApparitionAnchors.capacity} — and reaches exactly this number at mastery, so nobody who already
     * filled a list under the old flat cap loses anything.
     */
    public static final int MAX_POINTS = 12;

    public static final PlayerApparitionPoints EMPTY = new PlayerApparitionPoints(List.of());

    public PlayerApparitionPoints {
        points = List.copyOf(points);
    }

    public static final Codec<PlayerApparitionPoints> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            ApparitionPoint.CODEC.listOf().optionalFieldOf("points", List.of())
                    .forGetter(PlayerApparitionPoints::points)
    ).apply(instance, PlayerApparitionPoints::new));

    public boolean isFull() {
        return points.size() >= MAX_POINTS;
    }

    @Nullable
    public ApparitionPoint byName(String name) {
        for (ApparitionPoint point : points) {
            if (point.matches(name)) {
                return point;
            }
        }
        return null;
    }

    @Nullable
    public ApparitionPoint byIndex(int index) {
        return index >= 0 && index < points.size() ? points.get(index) : null;
    }

    /** True when {@code capacity} destinations are already held. */
    public boolean isFull(int capacity) {
        return points.size() >= Math.min(capacity, MAX_POINTS);
    }

    /** Adds {@code point}, replacing any existing point with the same name. Returns {@code this} if full. */
    public PlayerApparitionPoints with(ApparitionPoint point) {
        return with(point, MAX_POINTS);
    }

    /**
     * Adds {@code point} against a per-wizard {@code capacity}, replacing any existing point with the same
     * name. Naming an existing destination is how you replace one at a full set; a new name into a full set
     * returns {@code this} unchanged.
     */
    public PlayerApparitionPoints with(ApparitionPoint point, int capacity) {
        List<ApparitionPoint> next = new ArrayList<>(points);
        next.removeIf(existing -> existing.matches(point.name()));
        if (next.size() >= Math.min(capacity, MAX_POINTS)) {
            return this;
        }
        next.add(point);
        return new PlayerApparitionPoints(next);
    }

    /** Drops the point with {@code name}; returns {@code this} unchanged when nothing matched. */
    public PlayerApparitionPoints without(String name) {
        List<ApparitionPoint> next = new ArrayList<>(points);
        return next.removeIf(existing -> existing.matches(name)) ? new PlayerApparitionPoints(next) : this;
    }
}
