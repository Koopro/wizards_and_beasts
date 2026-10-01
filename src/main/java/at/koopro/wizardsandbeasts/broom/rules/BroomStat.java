package at.koopro.wizardsandbeasts.broom.rules;

import at.koopro.wizardsandbeasts.broom.BroomDefinition;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.Locale;
import java.util.Map;

/**
 * The broom flight numbers an administrator may tune, each with the bounds and step the admin slider uses. Every one
 * is a field the flight code already reads off {@link BroomDefinition} — nothing here adds a mechanic.
 *
 * <p>Bounds are chosen around the shipped roster (school broom 0.55 max speed … Firebolt Supreme 1.35) with room to
 * go either way, never so far that a value stops meaning what the flight code expects: turn and climb rates stay
 * positive, ratings stay within 0–1, a boost never slows a broom down.
 */
@NullMarked
public enum BroomStat {
    MAX_SPEED(0.1f, 2.0f, 0.01f),
    ACCELERATION(0.005f, 0.3f, 0.005f),
    DECELERATION(0.005f, 0.3f, 0.005f),
    BOOST_MULTIPLIER(1.0f, 3.0f, 0.05f),
    TURN_SPEED(0.1f, 2.0f, 0.05f),
    ASCENT_SPEED(0.05f, 1.0f, 0.01f),
    DESCENT_SPEED(0.05f, 1.0f, 0.01f),
    HANDLING_RATING(0.0f, 1.0f, 0.05f),
    STABILITY_RATING(0.0f, 1.0f, 0.05f);

    private final float min;
    private final float max;
    private final float step;

    BroomStat(float min, float max, float step) {
        this.min = min;
        this.max = max;
        this.step = step;
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public float min() {
        return min;
    }

    public float max() {
        return max;
    }

    public float step() {
        return step;
    }

    public static @Nullable BroomStat byId(String id) {
        for (BroomStat stat : values()) {
            if (stat.id().equals(id)) {
                return stat;
            }
        }
        return null;
    }

    public float read(BroomDefinition d) {
        return switch (this) {
            case MAX_SPEED -> d.maxSpeed();
            case ACCELERATION -> d.acceleration();
            case DECELERATION -> d.deceleration();
            case BOOST_MULTIPLIER -> d.boostMultiplier();
            case TURN_SPEED -> d.turnSpeed();
            case ASCENT_SPEED -> d.ascentSpeed();
            case DESCENT_SPEED -> d.descentSpeed();
            case HANDLING_RATING -> d.handlingRating();
            case STABILITY_RATING -> d.stabilityRating();
        };
    }

    /** {@code d} with these stats replaced; every other field — model, audio, seat, handling profile — kept. */
    public static BroomDefinition apply(BroomDefinition d, Map<BroomStat, Float> values) {
        if (values.isEmpty()) {
            return d;
        }
        float[] v = new float[values().length];
        for (BroomStat stat : values()) {
            v[stat.ordinal()] = values.getOrDefault(stat, stat.read(d));
        }
        return new BroomDefinition(d.id(), d.displayName(), d.tier(),
                v[MAX_SPEED.ordinal()], v[ACCELERATION.ordinal()], v[DECELERATION.ordinal()], v[BOOST_MULTIPLIER.ordinal()],
                d.boostDurationTicks(), d.boostCooldownTicks(), d.weakGravity(), d.lerpFactor(),
                v[TURN_SPEED.ordinal()], v[ASCENT_SPEED.ordinal()], v[DESCENT_SPEED.ordinal()],
                v[HANDLING_RATING.ordinal()], v[STABILITY_RATING.ordinal()],
                d.durability(), d.repairMaterial(), d.loreLines(), d.modelSlots(), d.woodTint(), d.assets(),
                d.handling(), d.audio(), d.seat());
    }
}
