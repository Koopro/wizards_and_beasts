package at.koopro.wizardsandbeasts.entity.creature.ai;

import at.koopro.wizardsandbeasts.creature.profile.CombatProfile;
import at.koopro.wizardsandbeasts.creature.profile.CombatProfile.Style;

/**
 * The beat of one creature's fight, with no world in it: given what the creature can see this tick, it says where
 * the creature should be going and whether a blow starts, lands or misses.
 *
 * <p>{@link CreatureCombatGoal} feeds it distances and turns its answers into navigation and damage. Keeping the
 * decisions here means every style can be tested tick by tick without a server.
 *
 * <p>The five styles differ in three places only:
 * <ul>
 *   <li><b>before the first blow:</b> an {@code AMBUSHER} holds still until the target is close, it is hurt, or its
 *       patience runs out (long if its profile names an ambush range, a short crouch if not);</li>
 *   <li><b>between blows:</b> a {@code HARRIER} (and a {@code PACK_HUNTER} with no pack beside it) backs off to its
 *       preferred range, a {@code SKIRMISHER} never leaves that range by choice, everything else stands its
 *       recovery out where it struck;</li>
 *   <li><b>reach of the blow:</b> an ambusher and a harrier lunge, so their windup can still connect a step
 *       further out.</li>
 * </ul>
 * A windup is a promise: once it starts the creature is committed and stands still, and the blow is resolved when
 * the windup ends. A target that has left reach by then is missed.
 */
public final class CombatRhythm {

    /** Vanilla's gap between two melee blows; a profile's recovery is added on top. */
    public static final int BASE_COOLDOWN = 20;
    /** How long an ambusher that names an ambush range will lie in wait for prey that does not come closer. */
    public static final int AMBUSH_PATIENCE = 100;
    /**
     * How long an ambusher that names no range holds before it pounces: a crouch, not a wait. Seen in a client
     * 2026-11-11: a hodag, a red cap and an acromantula stood in plain view for five seconds, six blocks from their
     * target, which read as a creature that had stopped working.
     */
    public static final int CROUCH_TICKS = 30;
    /** Ambush trigger distance when the profile names no preferred range. */
    public static final double DEFAULT_AMBUSH_RANGE = 5.0;
    /** Distance a harrier or skirmisher keeps when the profile names none. */
    public static final double DEFAULT_STATION_RANGE = 6.0;
    /** A harrier stays out at least this long after a blow. */
    public static final int MIN_BACK_OFF = 30;
    /** How long a creature that breaks off keeps running before it gives the fight up. */
    public static final int FLEE_TICKS = 100;

    /** Where the fight stands. */
    public enum Phase { WAIT, CLOSE, WINDUP, RECOVER, BACK_OFF, FLEE }

    /** What the body should do this tick. */
    public enum Move {
        /** Path to the target. */
        APPROACH,
        /** Stop and face the target. */
        STAND,
        /** Keep {@link Step#range()} blocks from the target, circling. */
        STATION,
        /** Get away from the target. */
        AWAY
    }

    /** What happened this tick, at most one thing. */
    public enum Event {
        NONE,
        /** A windup began: show it. */
        TELEGRAPH,
        /** The blow lands. */
        STRIKE,
        /** The blow was thrown and the target was no longer there. */
        WHIFF,
        /** Health fell under the profile's threshold: the creature turns to run. */
        BREAK_OFF,
        /** It has run far or long enough: drop the target. */
        DISENGAGE
    }

    /**
     * One tick's answer.
     *
     * @param speed navigation speed multiplier for {@code move}
     * @param range distance to hold when {@code move} is {@code STATION}
     */
    public record Step(Phase phase, Move move, Event event, double speed, double range) {}

    /**
     * What the creature knows this tick.
     *
     * @param distance   blocks to the target
     * @param inReach    the target can be hit from here without moving
     * @param lungeReach the target can be hit by a committed blow (reach plus the style's lunge)
     * @param health     own health as a fraction of maximum
     * @param provoked   the target has hurt it during this fight
     * @param allies     others of its kind fighting the same target nearby
     */
    public record Input(double distance, boolean inReach, boolean lungeReach, float health, boolean provoked,
                        int allies) {}

    private final CombatProfile profile;
    private Phase phase;
    private int timer;
    private int cooldown;
    /** Set once it has run from this wound, so a cornered creature fights instead of running for ever. */
    private boolean hasFled;
    /** Whether a pack stood with it on the last tick; decides what a pack hunter does after a blow. */
    private boolean lastAllies;

    public CombatRhythm(CombatProfile profile) {
        this.profile = profile;
        this.phase = Phase.CLOSE;
    }

    /** A new fight begins. The memory of having fled is kept: see {@link #step}. */
    public void engage() {
        phase = profile.style() == Style.AMBUSHER ? Phase.WAIT : Phase.CLOSE;
        timer = 0;
        cooldown = 0;
    }

    public Phase phase() {
        return phase;
    }

    /** Extra reach of a committed blow, in blocks, on top of ordinary melee reach. */
    public static double lunge(Style style) {
        return style == Style.AMBUSHER || style == Style.HARRIER ? 1.25 : 0.5;
    }

    /** Advance one tick. */
    public Step step(Input in) {
        lastAllies = in.allies() > 0;
        if (cooldown > 0) {
            cooldown--;
        }
        float threshold = profile.retreatAtHealth();
        if (threshold > 0.0f) {
            if (in.health() >= threshold) {
                hasFled = false;   // healed: it may lose its nerve again next time
            } else if (!hasFled && phase != Phase.FLEE && phase != Phase.WINDUP) {
                hasFled = true;
                phase = Phase.FLEE;
                timer = FLEE_TICKS;
                return new Step(phase, Move.AWAY, Event.BREAK_OFF, fleeSpeed(), 0.0);
            }
        }
        return switch (phase) {
            case FLEE -> flee();
            case WAIT -> lieInWait(in);
            case WINDUP -> windUp(in);
            case RECOVER -> recover();
            case BACK_OFF -> backOff(in);
            case CLOSE -> close(in);
        };
    }

    private Step flee() {
        if (--timer <= 0) {
            phase = Phase.CLOSE;
            return new Step(Phase.FLEE, Move.AWAY, Event.DISENGAGE, fleeSpeed(), 0.0);
        }
        return new Step(phase, Move.AWAY, Event.NONE, fleeSpeed(), 0.0);
    }

    private Step lieInWait(Input in) {
        timer++;
        double trigger = profile.preferredRange() > 0.0 ? profile.preferredRange() : DEFAULT_AMBUSH_RANGE;
        int patience = profile.preferredRange() > 0.0 ? AMBUSH_PATIENCE : CROUCH_TICKS;
        if (in.provoked() || in.distance() <= trigger || timer >= patience) {
            phase = Phase.CLOSE;
            timer = 0;
            return close(in);
        }
        return new Step(phase, Move.STAND, Event.NONE, 0.0, 0.0);
    }

    private Step close(Input in) {
        if (in.inReach() && cooldown <= 0) {
            if (profile.windupTicks() > 0) {
                phase = Phase.WINDUP;
                timer = profile.windupTicks();
                return new Step(phase, Move.STAND, Event.TELEGRAPH, 0.0, 0.0);
            }
            return afterBlow(Event.STRIKE);
        }
        if (profile.style() == Style.SKIRMISHER && !in.inReach()) {
            return new Step(phase, Move.STATION, Event.NONE, profile.approachSpeed(), stationRange());
        }
        return new Step(phase, Move.APPROACH, Event.NONE, profile.approachSpeed(), 0.0);
    }

    private Step windUp(Input in) {
        if (--timer > 0) {
            return new Step(phase, Move.STAND, Event.NONE, 0.0, 0.0);
        }
        return afterBlow(in.lungeReach() ? Event.STRIKE : Event.WHIFF);
    }

    private Step afterBlow(Event blow) {
        cooldown = BASE_COOLDOWN + profile.recoveryTicks();
        if (backsOffAfterABlow()) {
            phase = Phase.BACK_OFF;
            timer = Math.max(MIN_BACK_OFF, profile.recoveryTicks());
            return new Step(phase, Move.STATION, blow, profile.approachSpeed(), stationRange());
        }
        if (profile.recoveryTicks() > 0) {
            phase = Phase.RECOVER;
            timer = profile.recoveryTicks();
            return new Step(phase, Move.STAND, blow, 0.0, 0.0);
        }
        phase = Phase.CLOSE;
        return new Step(phase, Move.APPROACH, blow, profile.approachSpeed(), 0.0);
    }

    private Step recover() {
        if (--timer <= 0) {
            phase = Phase.CLOSE;
        }
        return new Step(Phase.RECOVER, Move.STAND, Event.NONE, 0.0, 0.0);
    }

    private Step backOff(Input in) {
        // A pack that has arrived ends a lone hunter's caution at once.
        if (--timer <= 0 || (profile.style() == Style.PACK_HUNTER && in.allies() > 0)) {
            phase = Phase.CLOSE;
        }
        return new Step(Phase.BACK_OFF, Move.STATION, Event.NONE, profile.approachSpeed(), stationRange());
    }

    private boolean backsOffAfterABlow() {
        return switch (profile.style()) {
            case HARRIER, SKIRMISHER -> true;
            case PACK_HUNTER -> !lastAllies;
            default -> false;
        };
    }

    private double stationRange() {
        return profile.preferredRange() > 0.0 ? profile.preferredRange() : DEFAULT_STATION_RANGE;
    }

    private double fleeSpeed() {
        return Math.max(1.3, profile.approachSpeed());
    }
}
