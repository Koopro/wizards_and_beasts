package at.koopro.wizardsandbeasts.creature.ecology;

import at.koopro.wizardsandbeasts.creature.profile.CreatureReaction;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import static at.koopro.wizardsandbeasts.creature.ecology.EcologyCapability.*;

/**
 * Decides what a creature means to do from what it perceived. Pure: a profile and a {@link Perception} in, a
 * {@link Decision} out — which is what makes a behaviour testable on its own ({@code EcologyBrainTest}).
 *
 * <p>The order below is the whole policy, top first:
 * <ol>
 *   <li>a reaction still in force (a spell nearby, kin dying, being hurt);</li>
 *   <li>survival: a threat in range, being badly hurt, a shunned or untrusted person too close;</li>
 *   <li>ground: an intruder on its territory — warned, then driven off;</li>
 *   <li>food: a hungry predator with prey in reach;</li>
 *   <li>the day: a nocturnal creature finds cover, then rests — and a resting creature only notices a person who
 *       walks right up to it, so a quiet approach finds it asleep;</li>
 *   <li>its own kind, its home, things on the ground, and — last — people it is curious about or fond of.</li>
 * </ol>
 * A bonded creature keeps its survival instincts and its appetite for treasure but holds no territory, keeps no
 * herd, rests on its person's schedule and does not avoid people: belonging to someone is what it means to be kept.
 */
@NullMarked
public final class EcologyBrain {

    /** A threat is fled inside this share of the sense radius. */
    static final double FLEE_SHARE = 0.75;
    /** An avoidant creature keeps an untrusted person at least this far off (or its sense radius, if smaller). */
    static final double AVOID_DISTANCE = 10.0;
    /** Badly hurt, a cowardly creature runs. */
    static final float COWARD_HEALTH = 0.35f;
    /** A resting creature notices a person only this close, and never one who is sneaking. */
    static final double WAKE_DISTANCE = 4.0;
    /** Curiosity and friendliness stop this close; nearer is the person's business. */
    static final double CLOSE_ENOUGH = 3.0;

    /** An intent, and the capability that produced it (what a watching player learns). */
    public record Decision(EcologyIntent intent, @Nullable EcologyCapability cause) {
        public static final Decision NONE = new Decision(EcologyIntent.NONE, null);
    }

    private EcologyBrain() {}

    public static Decision decide(EcologyProfile profile, Perception p) {
        if (profile.isEmpty()) {
            return Decision.NONE;
        }

        // 1. A reaction still in force.
        if (p.stimulus() != null) {
            Decision answered = answer(p.stimulus().response());
            if (answered != null) {
                return answered;
            }
        }

        // 2. Survival.
        if (p.threat().within(profile.senseRadius() * FLEE_SHARE)
                && (profile.has(PREY) || profile.has(FEAR_SENSITIVE) || profile.has(COWARDLY))) {
            return new Decision(EcologyIntent.FLEE_PREDATOR, profile.has(PREY) ? PREY
                    : profile.has(FEAR_SENSITIVE) ? FEAR_SENSITIVE : COWARDLY);
        }
        if (profile.has(COWARDLY) && p.health() < COWARD_HEALTH
                && (p.player().within(profile.senseRadius()) || p.threat().within(profile.senseRadius()))) {
            return new Decision(EcologyIntent.FLEE_HURT, COWARDLY);
        }

        boolean resting = profile.has(NOCTURNAL) && !p.night() && !p.bonded();
        // A resting creature does not see a quiet person, and sees a loud one only at its side.
        Perception.PlayerView person = resting && (p.player().sneaking() || p.player().distance() > WAKE_DISTANCE)
                ? Perception.PlayerView.NONE : p.player();

        if (profile.has(MAGICAL_CORRUPTION_SENSITIVE) && person.shunned() && person.within(profile.senseRadius())) {
            return new Decision(EcologyIntent.FLEE_PLAYER, MAGICAL_CORRUPTION_SENSITIVE);
        }
        if (profile.has(HUMAN_AVOIDANT) && !p.bonded() && !person.trusted()
                && person.within(Math.min(AVOID_DISTANCE, profile.senseRadius()))) {
            return new Decision(EcologyIntent.FLEE_PLAYER, HUMAN_AVOIDANT);
        }

        // 3. Ground.
        if (profile.has(TERRITORIAL) && !p.bonded() && !person.trusted() && person.within(profile.territoryRadius())) {
            boolean close = person.distance() <= profile.territoryRadius() * 0.5;
            if (profile.has(AGGRESSIVE) || close) {
                return new Decision(EcologyIntent.DEFEND_TERRITORY, profile.has(AGGRESSIVE) ? AGGRESSIVE : TERRITORIAL);
            }
            return new Decision(EcologyIntent.WARN_INTRUDER, TERRITORIAL);
        }

        // 4. Food.
        if (profile.has(PREDATOR) && !p.fed() && !p.bonded() && p.prey().within(profile.senseRadius())
                && (!profile.has(NOCTURNAL) || p.night())) {
            return new Decision(EcologyIntent.HUNT, PREDATOR);
        }

        // 5. The day.
        if (resting) {
            return new Decision(p.exposed() ? EcologyIntent.SEEK_SHELTER : EcologyIntent.REST, NOCTURNAL);
        }

        // 6. Kin, home, the ground, people.
        if (profile.has(HERD) && !p.bonded() && p.kin().count() > 0
                && p.kin().distanceToCentre() > profile.herdRadius()) {
            return new Decision(EcologyIntent.REGROUP, HERD);
        }
        if ((profile.has(TREE_DWELLING) || profile.has(TERRITORIAL)) && !p.bonded()
                && p.distanceFromHome() > profile.territoryRadius()) {
            return new Decision(EcologyIntent.RETURN_HOME, profile.has(TREE_DWELLING) ? TREE_DWELLING : TERRITORIAL);
        }
        if ((profile.has(TREASURE_SEEKER) || profile.has(SCAVENGER)) && p.item().within(profile.senseRadius())) {
            return new Decision(EcologyIntent.SEEK_ITEM, profile.has(TREASURE_SEEKER) ? TREASURE_SEEKER : SCAVENGER);
        }
        if (profile.has(CURIOUS) && !person.shunned() && (person.trusted() || person.sneaking())
                && person.within(profile.senseRadius()) && person.distance() > CLOSE_ENOUGH) {
            return new Decision(EcologyIntent.INVESTIGATE, CURIOUS);
        }
        if (profile.has(HUMAN_FRIENDLY) && !p.bonded() && !person.shunned()
                && person.within(profile.senseRadius()) && person.distance() > CLOSE_ENOUGH + 2) {
            return new Decision(EcologyIntent.APPROACH_PLAYER, HUMAN_FRIENDLY);
        }
        return Decision.NONE;
    }

    /** What a reaction answer becomes; {@code null} when the creature does nothing ecological about it. */
    private static @Nullable Decision answer(CreatureReaction.Response response) {
        return switch (response) {
            case FLEE -> new Decision(EcologyIntent.FLEE_STIMULUS, null);
            case INVESTIGATE -> new Decision(EcologyIntent.INVESTIGATE, MAGICAL_SENSE);
            case ATTACK -> new Decision(EcologyIntent.DEFEND_TERRITORY, null);
            case STUN, BECOME_ALERT -> new Decision(EcologyIntent.WARN_INTRUDER, null);
            case IGNORE, SPECIAL -> null;
        };
    }
}
