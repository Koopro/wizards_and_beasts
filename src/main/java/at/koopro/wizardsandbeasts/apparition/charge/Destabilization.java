package at.koopro.wizardsandbeasts.apparition.charge;

import org.jspecify.annotations.NullMarked;

/**
 * Everything working against a wizard at the moment they let go. Captured by the charge, consumed by the
 * splinch resolver; a plain value so the ladder stays a pure function of it.
 *
 * @param damageInstances hits taken during Determination
 * @param movingFast      moving faster than a sneak at release
 * @param submerged       eyes in fluid at release
 * @param encumbered      inventory above 80% full at release
 * @param sideAlong       carrying or being carried, either flavour
 * @param licensed        holds an Apparition licence — its absence is a tax, never a wall
 */
@NullMarked
public record Destabilization(
        int damageInstances,
        boolean movingFast,
        boolean submerged,
        boolean encumbered,
        boolean sideAlong,
        boolean licensed) {

    /** A perfectly composed, licensed, solo attempt. */
    public static final Destabilization NONE = new Destabilization(0, false, false, false, false, true);

    public Destabilization {
        damageInstances = Math.max(0, damageInstances);
    }

    /** The same conditions, re-read as a side-along. Both parties splinch at the same tier. */
    public Destabilization asSideAlong() {
        return sideAlong ? this
                : new Destabilization(damageInstances, movingFast, submerged, encumbered, true, licensed);
    }
}
