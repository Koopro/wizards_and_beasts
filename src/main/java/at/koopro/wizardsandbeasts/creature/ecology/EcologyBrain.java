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
 *   <li>a hazard underfoot or on its hide;</li>
 *   <li>a reaction still in force (a spell nearby, kin dying, being hurt, a noise);</li>
 *   <li>survival: a threat in range, being badly hurt, a shunned or untrusted person too close;</li>
 *   <li>ground: an intruder on its territory — warned, then driven off;</li>
 *   <li>food: a hungry predator with prey in reach (stalked first, if it is a stalker);</li>
 *   <li>weather: a weather-shy creature under open sky in rain, or one in a climate it is not made for;</li>
 *   <li>the day: its sleeping hours — a nocturnal creature finds cover, then rests; a diurnal or crepuscular one
 *       sleeps; and anything worn out sleeps — and a sleeping creature only notices a person who walks right up to
 *       it, so a quiet approach finds it asleep;</li>
 *   <li>needs, by utility: the most pressing of thirst, hunger and loneliness that it can do something about;</li>
 *   <li>its own kind (kept off, or rejoined), its home, things on the ground and remains, and — last — people it is
 *       curious about or fond of;</li>
 *   <li>calm and sated, it grooms.</li>
 * </ol>
 * A bonded creature keeps its survival instincts and its appetite for treasure but holds no territory, keeps no
 * herd, rests on its person's schedule, does not avoid people, and only leaves its person for a need that has
 * become urgent: belonging to someone is what it means to be kept.
 *
 * <p>Personality ({@link EcologyPersonality}) moves thresholds within 0.8–1.2 — how far off a threat is fled, how
 * close an intruder gets before the charge — and never which branches a species can take. With a neutral
 * personality and no needs, the policy is exactly the one before needs existed.
 *
 * <p>Holding a decision is not this class's job: {@link EcologyCommitment} decides whether a new decision may
 * replace the current one, and {@link #sustains} says whether the current one still makes sense — with looser
 * thresholds than starting it, which is the hysteresis that stops a creature flickering at a boundary.
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
    /** A stalker charges from this close; further off it creeps. */
    static final double STRIKE_DISTANCE = 8.0;
    /** A need is acted on from this pressure... */
    static final float NEED_START = 0.55f;
    /** ...a kept creature leaves its person for one only from this pressure... */
    static final float NEED_URGENT = 0.8f;
    /** ...and an act that answers a need goes on until it is down to this. */
    static final float NEED_DONE = 0.1f;
    /** This tired, a creature sleeps whatever the hour. */
    static final float EXHAUSTED = 0.9f;
    /** Above this, a fright still colours everything: threats are fled from further, curiosity waits. */
    static final float ALARMED = 0.5f;
    /** A lonely creature goes to its kind until this close. */
    static final double COMPANY = 4.0;
    /** Hotter than this (desert, badlands, savanna), a cold-adapted creature wants shade by day. */
    static final float HOT = 1.0f;
    /** Colder than this (snowy biomes), a warmth-loving creature wants cover. */
    static final float COLD = 0.3f;
    /** How bold a creature must be not to take cover from thunder when it is prey anyway. */
    static final float TIMID = 0.3f;

    /**
     * An intent, the capability that produced it (what a watching player learns), and how pressing it is (0–1),
     * which {@link EcologyCommitment} compares when two intents of the same band compete.
     */
    public record Decision(EcologyIntent intent, @Nullable EcologyCapability cause, float urgency) {
        public static final Decision NONE = new Decision(EcologyIntent.NONE, null, 0);

        public Decision(EcologyIntent intent, @Nullable EcologyCapability cause) {
            this(intent, cause, switch (intent.band()) {
                case EMERGENCY -> 1.0f;
                case DEFENCE -> 0.9f;
                case ALERT -> 0.8f;
                case ROUTINE -> 0.5f;
                case IDLE -> 0.0f;
            });
        }
    }

    private EcologyBrain() {}

    public static Decision decide(EcologyProfile profile, Perception p) {
        if (profile.isEmpty()) {
            return Decision.NONE;
        }
        EcologyPersonality me = p.personality();
        Perception.Surroundings world = p.surroundings();
        EcologyNeeds.View needs = p.needs();
        boolean alarmed = needs.safety() > ALARMED;

        // 0. A hazard: fire, lava or powder snow, whatever the creature is.
        if (world.hazard()) {
            return new Decision(EcologyIntent.FLEE_HAZARD, null);
        }

        // 1. A reaction still in force.
        if (p.stimulus() != null) {
            Decision answered = answer(p.stimulus());
            if (answered != null) {
                return answered;
            }
        }

        // 2. Survival.
        if (p.threat().within(fleeRadius(profile, me, alarmed))
                && (profile.has(PREY) || profile.has(FEAR_SENSITIVE) || profile.has(COWARDLY))) {
            return new Decision(EcologyIntent.FLEE_PREDATOR, profile.has(PREY) ? PREY
                    : profile.has(FEAR_SENSITIVE) ? FEAR_SENSITIVE : COWARDLY);
        }
        if (profile.has(COWARDLY) && p.health() < COWARD_HEALTH * me.lower(me.boldness())
                && (p.player().within(profile.senseRadius()) || p.threat().within(profile.senseRadius()))) {
            return new Decision(EcologyIntent.FLEE_HURT, COWARDLY);
        }

        boolean sleepingHours = sleepingHours(profile, p);
        // Asleep, a creature does not see a quiet person, and sees a loud one only at its side.
        Perception.PlayerView person = sleepingHours && (p.player().sneaking() || p.player().distance() > WAKE_DISTANCE)
                ? Perception.PlayerView.NONE : p.player();

        if (profile.has(MAGICAL_CORRUPTION_SENSITIVE) && person.shunned() && person.within(profile.senseRadius())) {
            return new Decision(EcologyIntent.FLEE_PLAYER, MAGICAL_CORRUPTION_SENSITIVE);
        }
        if (profile.has(HUMAN_AVOIDANT) && !p.bonded() && !person.trusted()
                && person.within(avoidDistance(profile, me))) {
            return new Decision(EcologyIntent.FLEE_PLAYER, HUMAN_AVOIDANT);
        }

        // 3. Ground.
        if (profile.has(TERRITORIAL) && !p.bonded() && !person.trusted() && person.within(profile.territoryRadius())) {
            boolean close = person.distance() <= profile.territoryRadius() * 0.5 * me.raise(me.aggression());
            if (profile.has(AGGRESSIVE) || close) {
                return new Decision(EcologyIntent.DEFEND_TERRITORY, profile.has(AGGRESSIVE) ? AGGRESSIVE : TERRITORIAL);
            }
            return new Decision(EcologyIntent.WARN_INTRUDER, TERRITORIAL);
        }

        // 4. Food: a hunter.
        if (profile.has(PREDATOR) && hungryForPrey(profile, p) && !p.bonded() && !sleepingHours
                && p.prey().within(profile.senseRadius())) {
            float urgency = profile.needs(EcologyNeed.HUNGER) ? Math.max(NEED_START, needs.hunger()) : 0.7f;
            boolean stalk = profile.huntStyle() == EcologyProfile.HuntStyle.STALK
                    && p.prey().distance() > STRIKE_DISTANCE;
            return new Decision(stalk ? EcologyIntent.STALK : EcologyIntent.HUNT, PREDATOR, urgency);
        }

        // 5. Weather and climate.
        Decision weather = weather(profile, p, me);
        if (weather != null) {
            return weather;
        }

        // 6. The day.
        if (sleepingHours) {
            if (profile.has(NOCTURNAL)) {
                return new Decision(p.exposed() ? EcologyIntent.SEEK_SHELTER : EcologyIntent.REST, NOCTURNAL, 0.6f);
            }
            return new Decision(EcologyIntent.SLEEP, profile.has(DIURNAL) ? DIURNAL : CREPUSCULAR, 0.6f);
        }
        if (profile.needs(EcologyNeed.ENERGY) && needs.energy() >= EXHAUSTED) {
            return new Decision(EcologyIntent.SLEEP, null, needs.energy());
        }

        // 7. Needs, by utility.
        Decision need = mostPressingNeed(profile, p);
        if (need != null) {
            return need;
        }

        // 8. Kin, home, the ground, people.
        if (profile.has(SOLITARY) && !p.bonded()
                && world.nearestKin().within(profile.personalSpace() * me.lower(me.sociability()))) {
            return new Decision(EcologyIntent.KEEP_DISTANCE, SOLITARY, 0.5f);
        }
        if (profile.has(HERD) && !p.bonded() && p.kin().count() > 0
                && p.kin().distanceToCentre() > profile.herdRadius() * me.lower(me.sociability())) {
            return new Decision(EcologyIntent.REGROUP, HERD, 0.5f);
        }
        if ((profile.has(TREE_DWELLING) || profile.has(TERRITORIAL)) && !p.bonded()
                && p.distanceFromHome() > profile.territoryRadius()) {
            return new Decision(EcologyIntent.RETURN_HOME, profile.has(TREE_DWELLING) ? TREE_DWELLING : TERRITORIAL, 0.4f);
        }
        if ((profile.has(TREASURE_SEEKER) || profile.has(SCAVENGER)) && p.item().within(profile.senseRadius())) {
            return new Decision(EcologyIntent.SEEK_ITEM, profile.has(TREASURE_SEEKER) ? TREASURE_SEEKER : SCAVENGER, 0.45f);
        }
        if (profile.has(SCAVENGER) && !p.bonded() && world.carrion().within(profile.senseRadius())) {
            return new Decision(EcologyIntent.SCAVENGE, SCAVENGER, 0.4f);
        }
        if (profile.has(CURIOUS) && !alarmed && !person.shunned() && person.within(profile.senseRadius())
                && person.distance() > CLOSE_ENOUGH && (person.trusted() || person.sneaking() || bored(profile, p))) {
            return new Decision(EcologyIntent.INVESTIGATE, CURIOUS, 0.35f);
        }
        if (profile.has(HUMAN_FRIENDLY) && !p.bonded() && !person.shunned()
                && person.within(profile.senseRadius()) && person.distance() > CLOSE_ENOUGH + 2) {
            return new Decision(EcologyIntent.APPROACH_PLAYER, HUMAN_FRIENDLY, 0.3f);
        }

        // 9. Calm and sated.
        if (profile.has(GROOMING) && !alarmed && needs.hunger() < 0.4f && needs.thirst() < 0.4f
                && !p.threat().within(profile.senseRadius())
                && (person.at() == null || person.trusted() || person.distance() > 8)) {
            return new Decision(EcologyIntent.GROOM, GROOMING, 0.2f);
        }
        return Decision.NONE;
    }

    /**
     * Whether {@code intent}, already under way, still makes sense. Deliberately looser than starting it — a threat is
     * fled until it is out of sense range, not just out of flee range; a meal goes on until hunger is nearly gone —
     * so a creature at a boundary does not start and stop on alternate looks.
     */
    public static boolean sustains(EcologyProfile profile, Perception p, EcologyIntent intent) {
        if (profile.isEmpty()) {
            return false;
        }
        EcologyNeeds.View needs = p.needs();
        Perception.Surroundings world = p.surroundings();
        Perception.PlayerView person = p.player();
        return switch (intent) {
            case NONE -> false;
            case FLEE_HAZARD -> world.hazard();
            case FLEE_PREDATOR -> p.threat().within(profile.senseRadius());
            case FLEE_PLAYER -> person.within(Math.max(avoidDistance(profile, p.personality()) * 1.3,
                    person.shunned() ? profile.senseRadius() : 0)) && (!person.trusted() || person.shunned());
            case FLEE_HURT -> p.health() < COWARD_HEALTH + 0.1f
                    && (person.within(profile.senseRadius()) || p.threat().within(profile.senseRadius()));
            case FLEE_STIMULUS -> p.stimulus() != null && p.stimulus().response() == CreatureReaction.Response.FLEE;
            case WARN_INTRUDER, DEFEND_TERRITORY -> !p.bonded() && ((!person.trusted()
                    && person.within(profile.territoryRadius() * 1.15)) || p.stimulus() != null);
            case HUNT, STALK -> !p.bonded() && p.prey().within(profile.senseRadius() * 1.25)
                    && (profile.needs(EcologyNeed.HUNGER) ? needs.hunger() > NEED_DONE : !p.fed());
            case REST -> sleepingHours(profile, p) && !p.exposed();
            case SEEK_SHELTER -> sleepingHours(profile, p) && p.exposed();
            case SLEEP -> sleepingHours(profile, p)
                    || (profile.needs(EcologyNeed.ENERGY) && needs.energy() > NEED_DONE * 3);
            // Under cover, it stays under cover until the weather passes — not just until it is out of the rain.
            case SHELTER_FROM_WEATHER -> weather(profile, p.withSurroundings(world.withWeather(
                    world.raining(), world.thundering(), true)), p.personality()) != null;
            case FORAGE -> needs.hunger() > NEED_DONE && (world.forage().seen() || p.item().seen());
            case DRINK -> needs.thirst() > NEED_DONE && world.water().seen();
            case SOCIALISE -> needs.social() > NEED_DONE && world.nearestKin().seen();
            case KEEP_DISTANCE -> world.nearestKin().within(profile.personalSpace() * 1.2);
            case GROOM -> !p.threat().within(profile.senseRadius())
                    && (person.at() == null || person.trusted() || person.distance() > 5);
            case REGROUP -> p.kin().count() > 0 && p.kin().distanceToCentre() > profile.herdRadius() * 0.6;
            case RETURN_HOME -> p.distanceFromHome() > profile.territoryRadius() * 0.5;
            case SEEK_ITEM -> p.item().within(profile.senseRadius());
            case SCAVENGE -> world.carrion().seen();
            case INVESTIGATE -> (p.stimulus() != null && p.stimulus().response() == CreatureReaction.Response.INVESTIGATE)
                    || (person.within(profile.senseRadius()) && person.distance() > CLOSE_ENOUGH && !person.shunned());
            case APPROACH_PLAYER -> person.within(profile.senseRadius()) && person.distance() > CLOSE_ENOUGH + 1;
        };
    }

    /** Its sleeping hours, by its rhythm. A kept creature keeps its person's hours instead. */
    static boolean sleepingHours(EcologyProfile profile, Perception p) {
        if (p.bonded()) {
            return false;
        }
        if (profile.has(NOCTURNAL)) {
            return !p.night();
        }
        if (profile.has(DIURNAL)) {
            return p.night();
        }
        if (profile.has(CREPUSCULAR)) {
            int t = p.surroundings().dayTime();
            return (t >= 4000 && t < 9500) || (t >= 16000 && t < 21000); // midday and the dead of night
        }
        return false;
    }

    private static boolean hungryForPrey(EcologyProfile profile, Perception p) {
        return profile.needs(EcologyNeed.HUNGER) ? p.needs().hunger() >= NEED_START : !p.fed();
    }

    private static double fleeRadius(EcologyProfile profile, EcologyPersonality me, boolean alarmed) {
        double r = profile.senseRadius() * FLEE_SHARE * me.lower(me.boldness()) * (alarmed ? 1.25 : 1.0);
        return Math.min(r, profile.senseRadius());
    }

    private static double avoidDistance(EcologyProfile profile, EcologyPersonality me) {
        return Math.min(AVOID_DISTANCE * me.lower(me.boldness()), profile.senseRadius());
    }

    /** A curious creature with a curiosity need that has built up, and the nerve to act on it. */
    private static boolean bored(EcologyProfile profile, Perception p) {
        return profile.needs(EcologyNeed.CURIOSITY) && p.needs().curiosity() >= NEED_START
                && p.personality().curiosity() >= 0.6f && p.personality().boldness() >= 0.4f;
    }

    private static @Nullable Decision weather(EcologyProfile profile, Perception p, EcologyPersonality me) {
        Perception.Surroundings world = p.surroundings();
        if (!world.skyAbove()) {
            return null;
        }
        if (profile.has(WEATHER_SHY) && (world.raining() || world.thundering())) {
            return new Decision(EcologyIntent.SHELTER_FROM_WEATHER, WEATHER_SHY, 0.7f);
        }
        if (world.thundering() && (profile.has(PREY) || profile.has(COWARDLY)) && me.boldness() < TIMID) {
            return new Decision(EcologyIntent.SHELTER_FROM_WEATHER, null, 0.7f);
        }
        if (profile.has(COLD_ADAPTED) && !p.night() && world.temperature() > HOT) {
            return new Decision(EcologyIntent.SHELTER_FROM_WEATHER, COLD_ADAPTED, 0.6f);
        }
        if (profile.has(WARMTH_LOVING) && (world.temperature() < COLD || world.raining())) {
            return new Decision(EcologyIntent.SHELTER_FROM_WEATHER, WARMTH_LOVING, 0.6f);
        }
        return null;
    }

    /** Thirst, hunger or loneliness — the most pressing one it can act on now, or {@code null}. */
    private static @Nullable Decision mostPressingNeed(EcologyProfile profile, Perception p) {
        EcologyNeeds.View needs = p.needs();
        Perception.Surroundings world = p.surroundings();
        float floor = p.bonded() ? NEED_URGENT : NEED_START;
        Decision best = null;
        if (profile.needs(EcologyNeed.THIRST) && needs.thirst() >= floor && world.water().seen()) {
            best = new Decision(EcologyIntent.DRINK, null, needs.thirst());
        }
        if (profile.needs(EcologyNeed.HUNGER) && needs.hunger() >= floor && (best == null || needs.hunger() > best.urgency())) {
            if ((profile.foragesBlocks() && world.forage().seen()) || p.item().seen()) {
                best = new Decision(EcologyIntent.FORAGE, dietOf(profile), needs.hunger());
            } else if (profile.has(SCAVENGER) && world.carrion().seen()) {
                best = new Decision(EcologyIntent.SCAVENGE, SCAVENGER, needs.hunger());
            }
        }
        if (profile.needs(EcologyNeed.SOCIAL) && !p.bonded() && needs.social() >= floor
                && world.nearestKin().seen() && world.nearestKin().distance() > COMPANY
                && (best == null || needs.social() * 0.9f > best.urgency())) {
            best = new Decision(EcologyIntent.SOCIALISE, profile.has(SOCIAL) ? SOCIAL : HERD, needs.social() * 0.9f);
        }
        return best;
    }

    /** The capability eating reveals. */
    private static @Nullable EcologyCapability dietOf(EcologyProfile profile) {
        if (profile.has(GRAZER)) {
            return GRAZER;
        }
        if (profile.has(BROWSER)) {
            return BROWSER;
        }
        if (profile.has(INSECTIVORE)) {
            return INSECTIVORE;
        }
        return profile.has(SCAVENGER) ? SCAVENGER : null;
    }

    /** What a reaction answer becomes; {@code null} when the creature does nothing ecological about it. */
    private static @Nullable Decision answer(Perception.Stimulus stimulus) {
        boolean magic = stimulus.kind() == null || stimulus.kind() == CreatureReaction.Stimulus.SPELL;
        return switch (stimulus.response()) {
            case FLEE -> new Decision(EcologyIntent.FLEE_STIMULUS, null);
            case INVESTIGATE -> new Decision(EcologyIntent.INVESTIGATE, magic ? MAGICAL_SENSE : CURIOUS, 0.6f);
            case ATTACK -> new Decision(EcologyIntent.DEFEND_TERRITORY, null);
            case STUN, BECOME_ALERT -> new Decision(EcologyIntent.WARN_INTRUDER, null);
            case IGNORE, SPECIAL -> null;
        };
    }
}
