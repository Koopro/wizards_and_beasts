package at.koopro.wizardsandbeasts.wand.visual;

import at.koopro.wizardsandbeasts.wand.allegiance.WandAllegianceRules;
import at.koopro.wizardsandbeasts.wand.allegiance.WandBondState;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.Optional;
import java.util.UUID;

/**
 * How awake a wand's core looks, given who (if anyone) is holding it.
 *
 * <p>In a hand — or in the local player's own inventory, which the game draws with that player as the
 * item's owner — this is the holder's {@link WandBondState}: a foreign wand is sullen and grey, a
 * mastered one bright, and only a loyal wand lets its light reach the pommel. A wand nobody holds (an
 * item frame, the ground) can only show whether it has a master at all.
 *
 * <p>Looks only. The cast path reads the bond through {@code WandAllegianceService}; nothing reads this.
 */
@NullMarked
public enum WandBondLook {
    /** It answers to no one yet: a wand on a shelf at Ollivanders, held or not. */
    DORMANT(0.40f, 0.85f, false),
    /** Nobody holds it, but it has a master somewhere. */
    AWAITING(0.50f, 1.00f, false),
    /** Held by someone it does not serve. */
    UNFAMILIAR(0.22f, 0.35f, false),
    RELUCTANT(0.45f, 0.80f, false),
    ACCEPTING(0.65f, 1.00f, false),
    LOYAL(0.85f, 1.00f, true),
    MASTERED(1.00f, 1.00f, true);

    private final float strength;
    private final float saturation;
    private final boolean reachesPommel;

    WandBondLook(float strength, float saturation, boolean reachesPommel) {
        this.strength = strength;
        this.saturation = saturation;
        this.reachesPommel = reachesPommel;
    }

    /** Multiplier on the core's light. */
    public float strength() {
        return strength;
    }

    /** How much of the core's colour survives, {@code [0, 1]}; the rest goes to grey. */
    public float saturation() {
        return saturation;
    }

    /** Whether the light shows at the pommel as well as the point. */
    public boolean reachesPommel() {
        return reachesPommel;
    }

    /**
     * @param master the wand's master, from the stack
     * @param bond   the wand's bond with that master, from the stack
     * @param holder whoever is holding (or, in an inventory, owns) the wand; null when nobody does
     */
    public static WandBondLook of(Optional<UUID> master, float bond, @Nullable UUID holder) {
        if (master.isEmpty()) {
            // A wand that has chosen no one is not sullen with whoever picks it up; it is simply asleep.
            return DORMANT;
        }
        if (holder == null) {
            return AWAITING;
        }
        return of(WandAllegianceRules.state(master.get().equals(holder), bond));
    }

    public static WandBondLook of(WandBondState state) {
        return switch (state) {
            case UNFAMILIAR -> UNFAMILIAR;
            case RELUCTANT -> RELUCTANT;
            case ACCEPTING -> ACCEPTING;
            case LOYAL -> LOYAL;
            case MASTERED -> MASTERED;
        };
    }
}
