package at.koopro.wizardsandbeasts.apparition.splinch;

import at.koopro.wizardsandbeasts.apparition.charge.ApparitionWindow;
import at.koopro.wizardsandbeasts.apparition.charge.Destabilization;
import org.jspecify.annotations.NullMarked;

/**
 * Turns a missed release into a consequence. Pure and static — no player, no level, no randomness — so the
 * whole ladder is unit-testable and a given miss always costs the same thing.
 *
 * <p>Replaces the old probabilistic roll. Splinching is now something you did, not something that happened
 * to you: the same release under the same conditions always lands on the same rung.
 */
@NullMarked
public final class SplinchResolver {

    /** Miss added per hit taken during Determination. */
    public static final int DAMAGE_MISS = 4;
    /** Miss added for letting go while moving faster than a sneak. */
    public static final int MOVEMENT_MISS = 2;
    /** Miss added for letting go with your eyes under a fluid. */
    public static final int SUBMERGED_MISS = 3;
    /** Miss added for letting go with a pack more than 80% full. */
    public static final int ENCUMBERED_MISS = 2;
    /**
     * Miss added for attempting it hungry.
     *
     * <p>Weighted with movement rather than with damage: going without food is a state a wizard chose to
     * travel in, not something done to them mid-charge, and it should cost about as much as being careless
     * on your feet. Apparition already charges food exhaustion on every attempt, so this is the far end of
     * the same idea — the wizard who keeps jumping without eating eventually pays for it in skin.
     */
    public static final int FAMISHED_MISS = 2;

    /** Applied after every additive term: two bodies are twice as hard to hold together as one. */
    public static final float SIDE_ALONG_MULTIPLIER = 2.0f;
    /** Applied last: unlicensed Apparition is illegal and clumsy, never impossible. */
    public static final float UNLICENSED_MULTIPLIER = 1.25f;

    private static final int MINOR_MAX = 4;
    private static final int MAJOR_MAX = 12;

    private SplinchResolver() {}

    /**
     * Inflates a raw miss by everything working against the wizard. Additive terms land first, multiplicative
     * ones after — so a side-along doubles the whole accumulated mess rather than only the timing error.
     *
     * <p>A forced discharge is returned untouched: it is already the worst outcome, and inflating a sentinel
     * would overflow.
     */
    public static int inflate(int missTicks, Destabilization destabilization) {
        if (ApparitionWindow.isForcedDischarge(missTicks)) {
            return missTicks;
        }
        int additive = missTicks
                + DAMAGE_MISS * destabilization.damageInstances()
                + (destabilization.movingFast() ? MOVEMENT_MISS : 0)
                + (destabilization.submerged() ? SUBMERGED_MISS : 0)
                + (destabilization.encumbered() ? ENCUMBERED_MISS : 0)
                + (destabilization.famished() ? FAMISHED_MISS : 0);

        float multiplied = additive;
        if (destabilization.sideAlong()) {
            multiplied *= SIDE_ALONG_MULTIPLIER;
        }
        if (!destabilization.licensed()) {
            multiplied *= UNLICENSED_MULTIPLIER;
        }
        return Math.round(multiplied);
    }

    /** The rung an already-inflated miss lands on. */
    public static SplinchTier resolve(int inflatedMissTicks) {
        if (ApparitionWindow.isForcedDischarge(inflatedMissTicks)) {
            return SplinchTier.CATASTROPHIC;
        }
        if (inflatedMissTicks <= 0) {
            return SplinchTier.CLEAN;
        }
        if (inflatedMissTicks <= MINOR_MAX) {
            return SplinchTier.MINOR;
        }
        if (inflatedMissTicks <= MAJOR_MAX) {
            return SplinchTier.MAJOR;
        }
        return SplinchTier.CATASTROPHIC;
    }

    /** Inflate, then resolve. The whole ladder in one call. */
    public static SplinchTier resolve(int missTicks, Destabilization destabilization) {
        return resolve(inflate(missTicks, destabilization));
    }

    /**
     * Raises an outcome to account for having been hit while holding it.
     *
     * <p><b>Applied after the ladder, never inside it.</b> The ladder answers "how badly was this released",
     * and inflation already charges four ticks of miss per hit inside that answer. This is a different
     * question — "was this wizard under fire at all" — and it is a floor rather than another additive term
     * because a floor cannot be out-run by good timing. A perfectly timed release while being shot is still
     * a release while being shot.
     *
     * <p>Under {@link WindupDamageMode#HYBRID}, in order:
     * <ul>
     *   <li>one hit floors to {@link SplinchTier#MINOR} — the jump lands, but never cleanly;</li>
     *   <li>an anchored hold, or a second hit, floors to {@link SplinchTier#MAJOR};</li>
     *   <li>carrying somebody floors to {@link SplinchTier#CATASTROPHIC}, which does not arrive.</li>
     * </ul>
     *
     * <p>The floors compose by taking the worst, so an anchored jump with a passenger under fire is
     * catastrophic rather than merely major, and an outcome the ladder already put <i>above</i> the floor is
     * left exactly where it was — this can only ever make an attempt worse, never better.
     *
     * @param computed          the rung the ladder chose
     * @param damageInstances   hits taken during the wind-up
     * @param anchored          whether this was an anchored hold rather than a blink
     * @param carryingPassenger whether a side-along was riding on this attempt
     */
    public static SplinchTier floorForWindupDamage(SplinchTier computed, int damageInstances,
                                                   boolean anchored, boolean carryingPassenger,
                                                   WindupDamageMode mode) {
        if (damageInstances <= 0 || mode == WindupDamageMode.LENIENT) {
            return computed;
        }
        if (mode == WindupDamageMode.CANCEL) {
            // The ladder's only rung that does not arrive. See WindupDamageMode.CANCEL for the price of
            // expressing "no journey" this way.
            return SplinchTier.CATASTROPHIC;
        }
        SplinchTier floor = SplinchTier.MINOR;
        if (anchored || damageInstances >= 2) {
            floor = SplinchTier.worseOf(floor, SplinchTier.MAJOR);
        }
        if (carryingPassenger) {
            floor = SplinchTier.worseOf(floor, SplinchTier.CATASTROPHIC);
        }
        return SplinchTier.worseOf(computed, floor);
    }
}
