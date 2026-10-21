package at.koopro.wizardsandbeasts.broom.fitting;

import org.jspecify.annotations.NullMarked;

import java.util.Locale;

/**
 * The broom characteristics a fitting or a worn handle can move. Every one is a field the flight code already reads
 * off {@code BroomDefinition} or its {@code BroomHandling}; a characteristic nothing reads cannot be fitted, because
 * an upgrade that changes a number no one looks at is a tooltip, not an upgrade.
 *
 * <p>All modifiers are multipliers. {@link #BOOST} multiplies only the boost's <em>excess</em> over 1, so a
 * modifier can never turn a boost into a brake.
 */
@NullMarked
public enum FittingStat {
    /** Cruising top speed ({@code maxSpeed}). Also what {@code BroomSpeedGuard} holds the rider to. */
    MAX_SPEED,
    /** How quickly it reaches that speed ({@code acceleration}). */
    ACCELERATION,
    /** How quickly it sheds speed with no input ({@code deceleration}). */
    DECELERATION,
    /** The boost's extra speed ({@code boostMultiplier − 1}). */
    BOOST,
    /** How long a boost lasts ({@code boostDurationTicks}). */
    BOOST_DURATION,
    /** Turn rate ({@code turnSpeed}). */
    TURN,
    /** How far it banks into a turn ({@code handlingRating}, kept within 0–1). */
    HANDLING,
    /** How quickly it settles from a lean ({@code stabilityRating}, kept within 0–1). */
    STABILITY,
    /** Heading wander while holding a line ({@code yawDrift}) — the gameplay half of "steady". */
    DRIFT,
    /** Heading shudder under boost ({@code wobbleAtBoost}). */
    WOBBLE,
    /** Damage the rider takes in a crash ({@code crashDamageMultiplier}). */
    CRASH,
    /** Durability lost per impact (minor and severe impact loss). */
    WEAR;

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** The lang key naming this characteristic in tooltips and the Travel tab. */
    public String translationKey() {
        return "broom_stat.wizards_and_beasts." + id();
    }

    /**
     * Whether a multiplier above 1 is good for the rider. Drives the tooltip colour only — a faster broom is
     * green, a broom that wanders more is red.
     */
    public boolean higherIsBetter() {
        return switch (this) {
            case DRIFT, WOBBLE, CRASH, WEAR -> false;
            default -> true;
        };
    }
}
