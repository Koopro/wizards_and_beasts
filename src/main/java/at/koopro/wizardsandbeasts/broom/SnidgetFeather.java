package at.koopro.wizardsandbeasts.broom;

import at.koopro.wizardsandbeasts.registry.ConsumableItemRegistry;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.NullMarked;

/**
 * A Golden Snidget feather held in the off hand while flying.
 *
 * <p>The Snidget was hunted to near-extinction for exactly this: it is the fastest thing with
 * feathers, and a broom that carries one borrows some of that. Held rather than fitted, which is the
 * whole shape of the trade — the off hand is a real cost to a flier who would otherwise be carrying a
 * wand, and the feather wears out whether or not the flight was worth it.
 *
 * <h2>Two effects, both small</h2>
 * <ul>
 *   <li><b>Eight percent more top speed</b>, applied after the config multiplier and the skill bonus
 *       so it scales with whatever the server has decided a broom is worth rather than around it.</li>
 *   <li><b>Less drift.</b> A broom's motion is lerped toward where it is pointed; raising that
 *       convergence is what "auto-stabilise" means in this physics model — the broom stops carrying
 *       old momentum through a turn. Clamped to the same ceiling {@code BroomDefinition}'s codec
 *       allows, so the feather can never push a broom outside its own authored envelope.</li>
 * </ul>
 *
 * <p>Everything here is pure arithmetic on purpose: what the feather is worth is exactly the kind of
 * number that gets nudged and never noticed again, so it can be pinned by a test.
 */
@NullMarked
public final class SnidgetFeather {

    /** Extra top speed while the feather is held. */
    public static final float SPEED_BONUS = 0.08f;

    /**
     * How much of the remaining gap to full convergence the feather closes.
     *
     * <p>A third, not all of it: a broom that snapped instantly to its facing would fly like a
     * cursor, and the drift is most of what makes a broom feel like a broom. This takes the edge off
     * rather than removing it.
     */
    public static final float STABILISE_FRACTION = 0.33f;

    /** Upper bound on the stabilised lerp, matching {@code BroomDefinition}'s own codec range. */
    public static final float MAX_LERP_FACTOR = 0.5f;

    /** Flight ticks per point of durability. Twenty seconds a point, so 64 is about 21 minutes aloft. */
    public static final int TICKS_PER_DURABILITY = 400;

    /** Total durability a feather carries. */
    public static final int DURABILITY = 64;

    private SnidgetFeather() {}

    /** Whether this rider is presenting a feather in their off hand. */
    public static boolean isHeld(Player rider) {
        return rider.getOffhandItem().is(ConsumableItemRegistry.GOLDEN_SNIDGET_FEATHER.get());
    }

    /** Top speed with the feather's bonus folded in. */
    public static float applySpeedBonus(float maxForwardSpeed) {
        return maxForwardSpeed * (1.0f + SPEED_BONUS);
    }

    /**
     * The broom's motion-convergence factor, stabilised.
     *
     * <p>Moves {@link #STABILISE_FRACTION} of the way from the authored value toward
     * {@link #MAX_LERP_FACTOR}, so a twitchy school broom gains more from the feather than a Firebolt
     * that was already tight — which is the right way round for a component that is meant to make a
     * broom easier to fly rather than to make the best broom better.
     */
    public static float stabilise(float lerpFactor) {
        float stabilised = lerpFactor + (MAX_LERP_FACTOR - lerpFactor) * STABILISE_FRACTION;
        return Mth.clamp(stabilised, lerpFactor, MAX_LERP_FACTOR);
    }

    /** Whether this flight tick is the one that costs a point of durability. */
    public static boolean wearsThisTick(int flightTicks) {
        return flightTicks > 0 && flightTicks % TICKS_PER_DURABILITY == 0;
    }

    /**
     * Charges one tick of flight against the held feather, breaking it when it runs out.
     *
     * <p>Called every tick and cheap on the ones that are not a wear tick, which is all but one in
     * four hundred.
     */
    public static void wear(Player rider, int flightTicks) {
        if (!wearsThisTick(flightTicks) || rider.level().isClientSide()) {
            return;
        }
        ItemStack feather = rider.getOffhandItem();
        if (!feather.is(ConsumableItemRegistry.GOLDEN_SNIDGET_FEATHER.get())) {
            return;
        }
        feather.hurtAndBreak(1, rider, InteractionHand.OFF_HAND);
    }
}
