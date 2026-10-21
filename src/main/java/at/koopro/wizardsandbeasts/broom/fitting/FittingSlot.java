package at.koopro.wizardsandbeasts.broom.fitting;

import org.jspecify.annotations.NullMarked;

import java.util.Locale;

/**
 * Where on a broom a fitting goes. One fitting per slot: a broom carries one charm on its handle, one set of tail
 * twigs, one binding and one coat of varnish, so two fittings of the same kind replace each other rather than stack.
 */
@NullMarked
public enum FittingSlot {
    /** A charm laid on the handle. */
    CHARM,
    /** The tail-twig bundle. */
    TWIGS,
    /** The binding that holds the bundle to the shaft. */
    BINDING,
    /** The finish on the handle. */
    VARNISH;

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public String translationKey() {
        return "broom_fitting_slot.wizards_and_beasts." + id();
    }
}
