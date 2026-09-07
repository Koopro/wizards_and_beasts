package at.koopro.wizardsandbeasts.heritage.nutrition;

import at.koopro.wizardsandbeasts.util.PlayerScopedState;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.food.FoodData;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * The half of a {@link NutritionPolicy} that every policy shares: keeping vanilla's {@link FoodData} in
 * line with a resource that is not food.
 *
 * <p><b>Why the food level is driven rather than hidden.</b> Cancelling the hunger HUD layer stops the
 * bar being drawn; it does not stop {@code FoodData} deciding whether the player may sprint
 * ({@code hasEnoughFood()}, {@code > 6}), whether natural regeneration runs ({@code >= 18}), and whether
 * they starve to death ({@code <= 0}). A vampire whose hunger bar was merely invisible would still starve
 * on a full blood pool, which is the bug this class exists to make impossible. So the food level becomes a
 * <em>mirror</em> of whatever the policy's real resource is, and those three vanilla rules keep working
 * with no special-casing: a well-fed vampire heals, a parched one cannot sprint, and none of them ever
 * reach food level 0, because starvation is the blood system's to inflict and not vanilla's.
 *
 * <p>Saturation is forced to zero for the same reason. Vanilla's fast-regeneration branch needs saturation
 * above zero, and it heals off the saturation pool — a pool no blood-drinker has. Leaving it at the
 * default 5 would have made every vampire regenerate several times faster than a wizard.
 *
 * <p>Exhaustion is deliberately <em>not</em> reset. There is no setter for it, and there does not need to
 * be: exhaustion only ever spends saturation and then food level, both of which are overwritten on the
 * next enforcement pass.
 */
@NullMarked
public final class NutritionEnforcer {

    /**
     * Food level a mirrored pool bottoms out at, rather than 0.
     *
     * <p>1, not 0, and that is the whole point: at 0 vanilla's own starvation timer starts hurting the
     * player on a schedule the blood system knows nothing about, on top of the damage the blood system is
     * already applying. One system owns starving.
     */
    private static final int MIRROR_FLOOR = 1;

    /** What a policy with no resource at all sits at: never hungry, never sprint-locked. */
    private static final int SATED_FOOD_LEVEL = 20;

    /**
     * Food level and saturation as they were when a non-vanilla eater started eating.
     *
     * <p>{@link PlayerScopedState} rather than a plain map so a player who logs out mid-bite cannot leave
     * an entry behind. Server-side only, which is where both the snapshot and the restore happen.
     */
    private static final PlayerScopedState<Snapshot> PRE_MEAL =
            PlayerScopedState.create("nutrition-pre-meal");

    private NutritionEnforcer() {}

    /**
     * Points vanilla's food level at the policy's real resource.
     *
     * @param resourcePercent the policy resource as a fraction of its maximum, {@code 0..1}. Ignored for
     *                        {@link NutritionPolicy#NONE}, which has no resource to mirror.
     */
    public static void driveVanillaHunger(ServerPlayer player, NutritionPolicy policy, float resourcePercent) {
        FoodData food = player.getFoodData();
        switch (policy) {
            case VANILLA -> {
                // Nothing. The bar is the resource.
            }
            case BLOOD -> {
                int mirrored = Mth.clamp(Math.round(Mth.clamp(resourcePercent, 0f, 1f) * 20f),
                        MIRROR_FLOOR, SATED_FOOD_LEVEL);
                if (food.getFoodLevel() != mirrored) {
                    food.setFoodLevel(mirrored);
                }
                if (food.getSaturationLevel() != 0f) {
                    food.setSaturation(0f);
                }
            }
            case NONE -> {
                if (food.getFoodLevel() != SATED_FOOD_LEVEL) {
                    food.setFoodLevel(SATED_FOOD_LEVEL);
                }
                if (food.getSaturationLevel() != 0f) {
                    food.setSaturation(0f);
                }
            }
        }
    }

    /**
     * Remembers what the food bar held before a bite, so {@link #restoreAfterMeal} can put it back.
     *
     * <p>The tick-by-tick mirror above would eventually overwrite any nutrition a meal granted anyway, but
     * "eventually" is up to half a second of free regeneration and a sprint the player should not have had.
     * Restoring on the spot means the meal is worth exactly nothing at the moment it lands, which is what
     * was actually promised.
     */
    public static void rememberBeforeMeal(ServerPlayer player) {
        FoodData food = player.getFoodData();
        PRE_MEAL.put(player, new Snapshot(food.getFoodLevel(), food.getSaturationLevel()));
    }

    /**
     * Undoes whatever a meal did to the food bar. A no-op when no snapshot was taken — the player was on
     * {@link NutritionPolicy#VANILLA} when they started chewing, or the start event never fired.
     */
    public static void restoreAfterMeal(ServerPlayer player) {
        @Nullable Snapshot snapshot = PRE_MEAL.remove(player.getUUID());
        if (snapshot == null) {
            return;
        }
        FoodData food = player.getFoodData();
        food.setFoodLevel(snapshot.foodLevel());
        food.setSaturation(snapshot.saturation());
    }

    /** Drops a pending snapshot without applying it — for a meal that was interrupted. */
    public static void forgetMeal(ServerPlayer player) {
        PRE_MEAL.remove(player.getUUID());
    }

    private record Snapshot(int foodLevel, float saturation) {}
}
