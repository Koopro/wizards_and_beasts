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
                + (destabilization.encumbered() ? ENCUMBERED_MISS : 0);

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
}
