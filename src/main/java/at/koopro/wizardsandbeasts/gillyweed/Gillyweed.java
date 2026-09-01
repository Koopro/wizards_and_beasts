package at.koopro.wizardsandbeasts.gillyweed;

import at.koopro.wizardsandbeasts.util.PlayerScopedState;
import net.minecraft.world.entity.player.Player;
import org.jspecify.annotations.NullMarked;

/**
 * Forty-five seconds of being something else.
 *
 * <p>Deliberately not Water Breathing with a different label. Harry does not hold his breath in the
 * Black Lake — he grows gills and webbed hands and <em>swims</em> like a thing that belongs there, and
 * the moment it wears off he is a boy at the bottom of a lake. All three of those are modelled: the
 * breathing, the speed, and the three seconds of warning before the change goes.
 *
 * <p><b>It replaces Water Breathing rather than stacking with it.</b> A wizard who drinks a potion
 * and then eats Gillyweed has not bought ninety seconds; they have wasted a potion. See
 * {@code GillyweedHandler}, which refuses the vanilla effect outright while the gills are in.
 */
@NullMarked
public final class Gillyweed {

    /** How long the gills last. */
    public static final int DURATION_TICKS = 900;      // 45s

    /** How much faster a gilled wizard swims. A percentage attribute: +80%. */
    public static final double SWIM_BONUS = 0.8;

    /** The warning before it goes. Three seconds is time to reach air if you started for it at once. */
    public static final int WARNING_TICKS = 60;        // 3s

    /**
     * Cooldown after the gills lapse, but only for somebody who chewed a second sprig.
     *
     * <p>Refreshing is allowed — you should be able to top up mid-dive rather than surfacing to plan.
     * It just costs you the ability to do it a third time straight away, which is what stops a stack
     * of Gillyweed being permanent aquatic life.
     */
    public static final int REFRESH_COOLDOWN_TICKS = 200;   // 10s

    /** Whether this player refreshed their current gills rather than growing them fresh. */
    private static final PlayerScopedState<Boolean> REFRESHED =
            PlayerScopedState.create("gillyweed_refreshed");

    private Gillyweed() {}

    /**
     * Records a chew and reports whether it was a refresh.
     *
     * @param alreadyGilled whether the eater already had the effect when they chewed
     */
    public static boolean chew(Player eater, boolean alreadyGilled) {
        if (alreadyGilled) {
            REFRESHED.put(eater, true);
            return true;
        }
        // A fresh growth clears the flag: the cooldown is for people who topped up, and the previous
        // dive's flag must not follow somebody into the next one.
        REFRESHED.remove(eater);
        return false;
    }

    /** Whether the gills currently in this player were topped up. */
    public static boolean wasRefreshed(Player eater) {
        return Boolean.TRUE.equals(REFRESHED.get(eater));
    }

    /** Forgets the refresh flag. Called when the gills end, however they end. */
    public static void clearRefresh(Player eater) {
        REFRESHED.remove(eater);
    }

    /**
     * Whether an effect with {@code remainingTicks} left is inside the warning window.
     *
     * <p>Pure, and worth being so: this is the number that decides whether a player drowns, and "did
     * the warning actually fire" is not something to find out at the bottom of a lake.
     */
    public static boolean isFading(int remainingTicks) {
        return remainingTicks > 0 && remainingTicks <= WARNING_TICKS;
    }
}
