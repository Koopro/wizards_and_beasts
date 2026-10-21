package at.koopro.wizardsandbeasts.creature.ecology;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * What a creature means to do next, as {@link EcologyBrain} decides it. {@code EcologyGoal} carries it out.
 *
 * <p>Each intent names the capability a player who watches it learns about, so a behaviour seen is a field note
 * written ({@link #reveals()}).
 */
@NullMarked
public enum EcologyIntent {
    /** Nothing ecological to do: wander, idle and the creature's own goals run as before. */
    NONE(null),
    /** Run from a predator or a source of dread. */
    FLEE_PREDATOR(EcologyCapability.PREY),
    /** Run from a person it does not trust, or one whose soul is dark. */
    FLEE_PLAYER(EcologyCapability.HUMAN_AVOIDANT),
    /** Badly hurt: break off and run. */
    FLEE_HURT(EcologyCapability.COWARDLY),
    /** Answer a stimulus by running from where it happened. */
    FLEE_STIMULUS(null),
    /** Stop and face an intruder on its ground; it is a warning, not yet an attack. */
    WARN_INTRUDER(EcologyCapability.TERRITORIAL),
    /** The intruder came on regardless, or an aggressive creature does not warn: drive them off. */
    DEFEND_TERRITORY(EcologyCapability.AGGRESSIVE),
    /** Hungry, with prey in reach. */
    HUNT(EcologyCapability.PREDATOR),
    /** Rest through the day, out of sight. */
    REST(EcologyCapability.NOCTURNAL),
    /** Daytime and exposed: find cover before resting. */
    SEEK_SHELTER(EcologyCapability.NOCTURNAL),
    /** Strayed from its own kind: rejoin them. */
    REGROUP(EcologyCapability.HERD),
    /** Too far from home: go back. */
    RETURN_HOME(EcologyCapability.TREE_DWELLING),
    /** Something shiny or edible on the ground. */
    SEEK_ITEM(EcologyCapability.TREASURE_SEEKER),
    /** Go and look: at a quiet person, or where magic was done. */
    INVESTIGATE(EcologyCapability.CURIOUS),
    /** Go to a person, unasked. */
    APPROACH_PLAYER(EcologyCapability.HUMAN_FRIENDLY);

    private final @Nullable EcologyCapability reveals;

    EcologyIntent(@Nullable EcologyCapability reveals) {
        this.reveals = reveals;
    }

    /** The capability a watching player learns from seeing this, or {@code null}. */
    public @Nullable EcologyCapability reveals() {
        return reveals;
    }

    public boolean flees() {
        return this == FLEE_PREDATOR || this == FLEE_PLAYER || this == FLEE_HURT || this == FLEE_STIMULUS;
    }
}
