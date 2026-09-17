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

    /** Scale of a reborn phoenix {@code ticksSinceRebirth} after bursting into flame. */
    public static float rebirthScale(long ticksSinceRebirth) {
        if (ticksSinceRebirth >= REBIRTH_GROWTH_TICKS) {
            return 1.0f;
        }
        float t = Math.max(0.0f, ticksSinceRebirth / (float) REBIRTH_GROWTH_TICKS);
        return REBORN_SCALE + (1.0f - REBORN_SCALE) * t;
    }

    // ── the Niffler ────────────────────────────────────────────────────────

    /**
     * How much a Niffler wants something shiny. Gold above all — Nifflers are "attracted to anything glittery", and
     * the goblins keep them to dig for gold — then precious stones and wizarding coin, then any other bright thing.
     *
     * @param itemPath the item's registry path, e.g. {@code gold_ingot}
     */
    public static int treasureValue(String itemPath) {
        if (itemPath.contains("gold") || itemPath.equals("galleon")) {
            return 3;
        }
        if (itemPath.equals("diamond") || itemPath.equals("emerald") || itemPath.equals("sickle")
                || itemPath.equals("netherite_ingot")) {
            return 2;
        }
        return 1;
    }

    // ── shedding ───────────────────────────────────────────────────────────

    /** Whether it is time to shed: the interval has run, and nothing shed before is still lying nearby. */
    public static boolean shedDue(int ticksUntilShed, boolean alreadyLyingNearby) {
        return ticksUntilShed <= 0 && !alreadyLyingNearby;
    }

    // ── the Bowtruckle ─────────────────────────────────────────────────────

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
