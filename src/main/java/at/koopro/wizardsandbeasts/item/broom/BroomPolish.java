package at.koopro.wizardsandbeasts.item.broom;

import at.koopro.wizardsandbeasts.registry.ModDataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * What a tin of broom polish actually does, in one place.
 *
 * <p>Servicing a broom is two separate things and they are worth keeping apart: it repairs the
 * handle, and it leaves the handle slick for a while. The repair is the reason to carry polish; the
 * slickness is the reason to use it before a race rather than after one.
 *
 * <h2>The window is stored as an absolute tick, and cleaned up by the item</h2>
 * {@code POLISHED_UNTIL_TICK} holds a game time, not a countdown, so a stack sitting in a chest does
 * not need ticking to stay honest. {@link BroomPolishItem} strips the component once it has passed,
 * which is what lets the tooltip and the entity both treat "component present" as "polished" without
 * either of them needing the clock.
 */
public final class BroomPolish {

    /** Twenty minutes, in ticks. Long enough to be worth using before a flight, not a permanent buff. */
    public static final int DURATION_TICKS = 20 * 60 * 20;

    /**
     * Share of a broom's maximum durability one tin restores.
     *
     * <p>A fraction rather than a flat 40: a flat figure is most of a Cleansweep and a rounding error
     * on an Oakshaft, so the same tin would be a full service on the cheap broom and pointless on the
     * expensive one — exactly backwards from how a consumable should scale.
     */
    public static final float REPAIR_FRACTION = 0.25f;

    /** How much of the heading wander a freshly polished broom loses. */
    public static final float WOBBLE_RELIEF = 0.5f;

    private BroomPolish() {}

    /** Durability one tin restores to this broom, always at least one point. */
    public static int repairAmount(ItemStack broom) {
        return Math.max(1, Math.round(broom.getMaxDamage() * REPAIR_FRACTION));
    }

    /** True when this broom would gain anything from being serviced. */
    public static boolean needsRepair(ItemStack broom) {
        return broom.getDamageValue() > 0;
    }

    /** True when the slickness is still on, by this level's clock. */
    public static boolean isPolished(ItemStack broom, Level level) {
        return isPolished(broom, level.getGameTime());
    }

    /**
     * As {@link #isPolished(ItemStack, Level)}, against a bare clock.
     *
     * <p>The overload that carries the rule. A window that can only be checked by handing it a live
     * {@code Level} is a window nobody can write a test for, and "does the polish actually wear off"
     * is precisely the question worth having a test for.
     */
    public static boolean isPolished(ItemStack broom, long gameTime) {
        Long until = broom.get(ModDataComponents.POLISHED_UNTIL_TICK.get());
        return until != null && gameTime < until;
    }

    /**
     * Repairs the broom and starts the window.
     *
     * @return true when anything changed, so the caller knows whether to spend the tin
     */
    public static boolean apply(ItemStack broom, Level level) {
        return apply(broom, level.getGameTime());
    }

    /** As {@link #apply(ItemStack, Level)}, against a bare clock. */
    public static boolean apply(ItemStack broom, long gameTime) {
        boolean repaired = needsRepair(broom);
        if (repaired) {
            broom.setDamageValue(Math.max(0, broom.getDamageValue() - repairAmount(broom)));
        }
        // Refreshes rather than stacks: polishing twice in a row is a wasted tin, not forty minutes.
        boolean alreadySlick = isPolished(broom, gameTime);
        broom.set(ModDataComponents.POLISHED_UNTIL_TICK.get(), gameTime + DURATION_TICKS);
        return repaired || !alreadySlick;
    }
}
