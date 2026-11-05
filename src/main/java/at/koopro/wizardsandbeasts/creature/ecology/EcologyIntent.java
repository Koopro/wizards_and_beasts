package at.koopro.wizardsandbeasts.creature.ecology;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import static at.koopro.wizardsandbeasts.creature.ecology.EcologyIntent.Band.*;

/**
 * What a creature means to do next, as {@link EcologyBrain} decides it and {@link EcologyCommitment} lets it.
 * {@code EcologyGoal} carries it out.
 *
 * <p>Each intent names the capability a player who watches it learns about, so a behaviour seen is a field note
 * written ({@link #reveals()}). Each also states how it is held ({@link EcologyCommitment}): its {@link Band} (what
 * may interrupt it), how long it is kept at least and at most, and how long it is not taken up again after it ends
 * or fails. Durations are in ticks.
 */
@NullMarked
public enum EcologyIntent {
    /** Nothing ecological to do: wander, idle and the creature's own goals run as before. */
    NONE(null, IDLE, 0, 0, 0, 0),
    /** Run from a predator or a source of dread. */
    FLEE_PREDATOR(EcologyCapability.PREY, EMERGENCY, 40, 300, 0, 20),
    /** Run from a person it does not trust, or one whose soul is dark. */
    FLEE_PLAYER(EcologyCapability.HUMAN_AVOIDANT, EMERGENCY, 40, 300, 0, 20),
    /** Badly hurt: break off and run. */
    FLEE_HURT(EcologyCapability.COWARDLY, EMERGENCY, 40, 300, 0, 20),
    /** Answer a stimulus by running from where it happened. */
    FLEE_STIMULUS(null, EMERGENCY, 40, 200, 0, 20),
    /** Standing in fire, lava or powder snow, or burning: get out. */
    FLEE_HAZARD(null, EMERGENCY, 10, 100, 0, 10),
    /** Stop and face an intruder on its ground; it is a warning, not yet an attack. */
    WARN_INTRUDER(EcologyCapability.TERRITORIAL, ALERT, 40, 600, 0, 40),
    /** The intruder came on regardless, or an aggressive creature does not warn: drive them off. */
    DEFEND_TERRITORY(EcologyCapability.AGGRESSIVE, DEFENCE, 40, 600, 60, 100),
    /** Hungry, with prey in reach. */
    HUNT(EcologyCapability.PREDATOR, ROUTINE, 60, 1200, 0, 400),
    /** Hungry, prey seen but far: creep closer, low and slow, before the charge. */
    STALK(EcologyCapability.PREDATOR, ROUTINE, 60, 1200, 0, 400),
    /** Rest through the day, out of sight. */
    REST(EcologyCapability.NOCTURNAL, ROUTINE, 200, 24000, 0, 0),
    /** Daytime and exposed: find cover before resting. */
    SEEK_SHELTER(EcologyCapability.NOCTURNAL, ROUTINE, 60, 600, 0, 200),
    /** Its sleeping hours (diurnal at night, crepuscular at midday and midnight), or simply worn out. */
    SLEEP(null, ROUTINE, 400, 14000, 0, 0),
    /** Rain, thunder, or a climate it is not made for, under open sky: get under cover, and stay there. */
    SHELTER_FROM_WEATHER(EcologyCapability.WEATHER_SHY, ROUTINE, 100, 3600, 0, 300),
    /** Hungry: graze, browse, pick at bark, or eat food on the ground. */
    FORAGE(null, ROUTINE, 100, 1200, 0, 300),
    /** Thirsty: walk to water and drink. */
    DRINK(null, ROUTINE, 60, 800, 0, 300),
    /** Lonely, with its kind in view: go and be near one. */
    SOCIALISE(EcologyCapability.SOCIAL, ROUTINE, 100, 600, 600, 300),
    /** Solitary, and one of its kind came too close: move off. */
    KEEP_DISTANCE(EcologyCapability.SOLITARY, ROUTINE, 40, 400, 100, 100),
    /** Calm and sated: a while spent grooming. */
    GROOM(EcologyCapability.GROOMING, ROUTINE, 60, 160, 2400, 600),
    /** Strayed from its own kind: rejoin them. */
    REGROUP(EcologyCapability.HERD, ROUTINE, 40, 600, 0, 100),
    /** Too far from home: go back. */
    RETURN_HOME(EcologyCapability.TREE_DWELLING, ROUTINE, 60, 1200, 0, 300),
    /** Something shiny or edible on the ground. */
    SEEK_ITEM(EcologyCapability.TREASURE_SEEKER, ROUTINE, 20, 600, 0, 200),
    /** Something died here lately: go and see what is left. */
    SCAVENGE(EcologyCapability.SCAVENGER, ROUTINE, 60, 1200, 1200, 400),
    /** Go and look: at a quiet person, a noise, or where magic was done. */
    INVESTIGATE(EcologyCapability.CURIOUS, ROUTINE, 40, 400, 300, 400),
    /** Go to a person, unasked. */
    APPROACH_PLAYER(EcologyCapability.HUMAN_FRIENDLY, ROUTINE, 40, 400, 600, 600);

    /** What may interrupt an intent: anything in a higher band, at once. */
    public enum Band {
        IDLE,
        /** Daily life: needs, rhythm, kin, home, curiosity. */
        ROUTINE,
        /** Something to face: a warning stare, a bristle at a noise. Interrupts daily life. */
        ALERT,
        /** Driving something off. An escalation from a warning, so it interrupts one at once. */
        DEFENCE,
        /** Running for its life, or out of the fire. */
        EMERGENCY
    }

    private final @Nullable EcologyCapability reveals;
    private final Band band;
    private final int minTicks;
    private final int maxTicks;
    private final int endCooldown;
    private final int failCooldown;

    EcologyIntent(@Nullable EcologyCapability reveals, Band band, int minTicks, int maxTicks, int endCooldown,
                  int failCooldown) {
        this.reveals = reveals;
        this.band = band;
        this.minTicks = minTicks;
        this.maxTicks = maxTicks;
        this.endCooldown = endCooldown;
        this.failCooldown = failCooldown;
    }

    /** The capability a watching player learns from seeing this, or {@code null}. */
    public @Nullable EcologyCapability reveals() {
        return reveals;
    }

    public Band band() {
        return band;
    }

    /** Held at least this long unless something more urgent comes up or it stops making sense. */
    public int minTicks() {
        return minTicks;
    }

    /** Never held longer than this: a hunt that cannot close, a rest that never ends. */
    public int maxTicks() {
        return maxTicks;
    }

    /** Not taken up again for this long after it ends normally (0: may resume at once). */
    public int endCooldown() {
        return endCooldown;
    }

    /** Not taken up again for this long after it fails (doubled per repeated failure, up to eight times). */
    public int failCooldown() {
        return failCooldown;
    }

    public boolean flees() {
        return this == FLEE_PREDATOR || this == FLEE_PLAYER || this == FLEE_HURT || this == FLEE_STIMULUS
                || this == FLEE_HAZARD;
    }

    /** Whether the creature means to lie still for it. */
    public boolean resting() {
        return this == REST || this == SLEEP;
    }
}
