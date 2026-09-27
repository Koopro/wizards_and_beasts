package at.koopro.wizardsandbeasts.creature.wildlife;

import at.koopro.wizardsandbeasts.bestiary.DiscoveryTier;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.NullMarked;

/**
 * The decisions that make magical creatures behave like wildlife rather than like mobs, as pure functions: who a
 * unicorn will let near, what a demiguise foresees, what a niffler wants most, when a bite passes on lycanthropy.
 *
 * <p>No world, no randomness. Every rule a player can run into is stated here, so it can be tested and explained.
 */
@NullMarked
public final class WildlifeRules {

    private WildlifeRules() {}

    // ── being watched ──────────────────────────────────────────────────────

    /** How squarely a player must be looking at a creature for it to know it is watched (about 21 degrees). */
    public static final double WATCHED_COSINE = 0.93;

    /** True when {@code look} (a unit vector) points at a creature {@code toCreature} away. */
    public static boolean looksAt(Vec3 look, Vec3 toCreature) {
        double length = toCreature.length();
        if (length < 1.0e-6) {
            return true;
        }
        return look.dot(toCreature.scale(1.0 / length)) >= WATCHED_COSINE;
    }

    // ── the Demiguise ──────────────────────────────────────────────────────

    /** Slower than this, a player is not approaching; they are standing about. Blocks per tick. */
    public static final double APPROACH_SPEED = 0.05;

    /** How directly a player must be heading at a Demiguise for it to foresee them (about 32 degrees). */
    public static final double FORESEEN_COSINE = 0.85;

    /**
     * Whether a Demiguise foresees this approach. It sees the most probable future, so a player walking straight
     * at it is seen coming; one who circles, stops, or comes at it sideways is not.
     *
     * @param movement   the player's horizontal movement last tick
     * @param toCreature from the player to the Demiguise
     */
    public static boolean foresees(Vec3 movement, Vec3 toCreature) {
        Vec3 flatMove = new Vec3(movement.x, 0.0, movement.z);
        Vec3 flatTo = new Vec3(toCreature.x, 0.0, toCreature.z);
        double speed = flatMove.length();
        double distance = flatTo.length();
        if (speed < APPROACH_SPEED || distance < 1.0e-6) {
            return false;
        }
        return flatMove.scale(1.0 / speed).dot(flatTo.scale(1.0 / distance)) >= FORESEEN_COSINE;
    }

    /**
     * Where a Demiguise that foresaw an approach steps to: sideways out of the path, not straight away, which is
     * what makes it look as if it knew. {@code side} is +1 or -1.
     */
    public static Vec3 sidestep(Vec3 toCreature, double distance, int side) {
        Vec3 flat = new Vec3(toCreature.x, 0.0, toCreature.z);
        double length = flat.length();
        Vec3 dir = length < 1.0e-6 ? new Vec3(1, 0, 0) : flat.scale(1.0 / length);
        Vec3 perpendicular = new Vec3(-dir.z, 0.0, dir.x).scale(side >= 0 ? 1 : -1);
        return perpendicular.scale(distance).add(dir.scale(distance * 0.5));
    }

    // ── the Unicorn ────────────────────────────────────────────────────────

    /** Dark corruption at or above which a unicorn will not let a wizard near. */
    public static final float PURITY_LIMIT = 25.0f;

    /** Dark corruption at or above which a unicorn flees a wizard from twice as far. */
    public static final float TAINT = 50.0f;

    /**
     * Whether a wary creature lets a player near: they have watched it calmly before (so it has watched them), and
     * they come quietly and empty-handed. A purity-sensitive creature — the unicorn — also refuses anyone who has
     * killed one of its kind or whose soul is darkened.
     */
    /**
     * {@code tier}, raised by however many tiers of handling a player has trained, capped at the top tier.
     *
     * <p>Pure so the Magizoology web's handling line can be tested without a creature: "as if you had studied
     * it one tier longer" is the promise the tooltip makes, and this is the whole of it.
     */
    public static DiscoveryTier trustedTier(DiscoveryTier tier, int trainedSteps) {
        if (trainedSteps <= 0) {
            return tier;
        }
        DiscoveryTier[] tiers = DiscoveryTier.values();
        return tiers[Math.min(tiers.length - 1, tier.ordinal() + trainedSteps)];
    }

    public static boolean letsNear(DiscoveryTier tier, boolean sneaking, boolean emptyHanded, boolean puritySensitive,
                                   boolean slayer, float corruption) {
        if (!tier.atLeast(DiscoveryTier.OBSERVED) || !sneaking || !emptyHanded) {
            return false;
        }
        return !puritySensitive || (!slayer && corruption < PURITY_LIMIT);
    }

    /** Whether a purity-sensitive creature flees this wizard on sight, from further than it flees anyone else. */
    public static boolean shuns(boolean slayer, float corruption) {
        return slayer || corruption >= TAINT;
    }

    // ── the Phoenix ────────────────────────────────────────────────────────

    /** A friend at or below this share of health, nearby, is wept for. */
    public static final float TEARS_HEALTH_FRACTION = 0.3f;
    public static final int TEARS_COOLDOWN_TICKS = 6000;
    /** A reborn phoenix grows back to full size over this long. */
    public static final int REBIRTH_GROWTH_TICKS = 6000;
    public static final float REBORN_SCALE = 0.45f;

    /** A reborn chick rises with this share of its health: "weak at first" (Fantastic Beasts). */
    public static final float REBORN_HEALTH_FRACTION = 0.5f;
    /** A phoenix this badly hurt, with its attacker close, leaves in a burst of flame. */
    public static final float FLAME_ESCAPE_HEALTH_FRACTION = 0.4f;
    /** Between one flame-travel and the next. */
    public static final int FLAME_TRAVEL_COOLDOWN_TICKS = 600;
    /** A bonded owner further than this (same dimension) is reached by flame rather than flight. */
    public static final double FLAME_RETURN_DISTANCE = 40.0;
    /** Between one song and the next. */
    public static final int SONG_COOLDOWN_TICKS = 2400;
    public static final int SONG_TICKS = 60;
    public static final double SONG_RANGE = 12.0;
    /** A phoenix's strike when it defends its person. Low, and never the killing blow. */
    public static final float PHOENIX_STRIKE_DAMAGE = 3.0f;
    /** A falling owner is caught once they have fallen this far. */
    public static final float CATCH_FALL_DISTANCE = 8.0f;

    /**
     * Damage a phoenix may deal to something with {@code targetHealth} left: never enough to kill it.
     *
     * <p>Fantastic Beasts: the phoenix "has never been known to kill". Fawkes blinded the basilisk in
     * <i>Chamber of Secrets</i>; Harry did the killing. So a phoenix defending its person wounds and blinds,
     * and stops at one heart.
     */
    public static float phoenixStrikeDamage(float targetHealth, float damage) {
        return Math.max(0.0f, Math.min(damage, targetHealth - 1.0f));
    }

    /** Whether a person needs a phoenix's song: badly hurt, or hurt by something in the last five seconds. */
    public static boolean inDanger(float health, float maxHealth, int ticksSinceHurtByMob) {
        return health <= maxHealth * 0.5f || ticksSinceHurtByMob < 100;
    }

    /** Scale of a reborn phoenix {@code ticksSinceRebirth} after bursting into flame. */
    public static float rebirthScale(long ticksSinceRebirth) {
        if (ticksSinceRebirth >= REBIRTH_GROWTH_TICKS) {
            return 1.0f;
        }
        float t = Math.max(0.0f, ticksSinceRebirth / (float) REBIRTH_GROWTH_TICKS);
        return REBORN_SCALE + (1.0f - REBORN_SCALE) * t;
    }

    // ── the Hippogriff ─────────────────────────────────────────────────────
    //
    // Prisoner of Azkaban: "You always wait fer the hippogriff ter make the firs' move. It's polite, see? You walk
    // towards him, and you bow, an' you wait. If he bows back, you're allowed ter touch him." Keep eye contact;
    // never insult one. Crouching is the bow.

    /** A bow must be held this long before the hippogriff answers it. */
    public static final int BOW_HOLD_TICKS = 30;
    /** A bow counts from this close. */
    public static final double BOW_RANGE = 8.0;
    /** A stranger who has not bowed and comes closer than this is warned off. */
    public static final double CROWD_DISTANCE = 2.5;
    /** A second crowding within this long of the warning is taken as an insult. */
    public static final int WARNING_MEMORY_TICKS = 200;
    /** Someone who struck it is not bowed back to for this long. */
    public static final int GRUDGE_TICKS = 6000;
    /** Bond at which a hippogriff lets the one who bowed to it ride. */
    public static final int HIPPOGRIFF_RIDE_BOND = 25;

    /**
     * Whether a player is bowing to a hippogriff: crouched, holding its gaze (looking at its head, which is what "keep
     * eye contact" asks for), standing still, and near enough to be seen doing it.
     *
     * @param lookDot  dot of the player's view vector with the direction to the hippogriff's eyes
     * @param speedSqr the player's horizontal speed squared
     */
    public static boolean bowing(boolean crouching, double lookDot, double speedSqr, double distance) {
        return crouching && lookDot > 0.9 && speedSqr < 0.003 && distance <= BOW_RANGE;
    }

    /** What an unbowed stranger crowding a hippogriff gets: nothing yet, a warning, or an attack. */
    public enum Crowding { NONE, WARN, ATTACK }

    /**
     * @param ticksSinceWarned ticks since this stranger was last warned off, or a negative number if never
     */
    public static Crowding crowding(boolean respected, double distance, int ticksSinceWarned) {
        if (respected || distance >= CROWD_DISTANCE) {
            return Crowding.NONE;
        }
        if (ticksSinceWarned >= 0 && ticksSinceWarned < WARNING_MEMORY_TICKS) {
            // A beat to back off after the warning before the insult is taken.
            return ticksSinceWarned > 30 ? Crowding.ATTACK : Crowding.NONE;
        }
        return Crowding.WARN;
    }

    // The Niffler's treasure ranking lives in tags now (NifflerTreasure), not here.

    // ── shedding ───────────────────────────────────────────────────────────

    /** Whether it is time to shed: the interval has run, and nothing shed before is still lying nearby. */
    public static boolean shedDue(int ticksUntilShed, boolean alreadyLyingNearby) {
        return ticksUntilShed <= 0 && !alreadyLyingNearby;
    }

    // ── the Bowtruckle ─────────────────────────────────────────────────────

    /**
     * Whether a Bowtruckle has vanished into the bark: still for long enough, pressed against a log, and not busy
     * defending its tree or working a lock.
     */
    public static boolean camouflaged(int stillTicks, boolean againstBark, boolean busy) {
        return !busy && againstBark && stillTicks >= at.koopro.wizardsandbeasts.entity.beast.BowtruckleEntity.CAMOUFLAGE_AFTER;
    }

    /** A block this close to a Bowtruckle's home tree is part of the tree. */
    public static final double HOME_TREE_RADIUS = 6.0;
    /** How long a Bowtruckle stays after someone who cut its tree. */
    public static final int DEFENCE_TICKS = 600;

    public static boolean partOfHomeTree(double distanceSquared) {
        return distanceSquared <= HOME_TREE_RADIUS * HOME_TREE_RADIUS;
    }

    // ── lycanthropy ────────────────────────────────────────────────────────

    /**
     * Whether a bite passes on lycanthropy. Only a werewolf in wolf form bites with the curse, only under the full
     * moon, only if it drew blood, and only a human can catch it; a server may turn infection off.
     */
    public static boolean infects(boolean enabled, boolean attackerTransformed, boolean fullMoonNight,
                                  boolean victimHuman, boolean victimAlreadyWerewolf, float damageDealt) {
        return enabled && attackerTransformed && fullMoonNight && victimHuman && !victimAlreadyWerewolf
                && damageDealt > 0.0f;
    }
}
