package at.koopro.wizardsandbeasts.brew;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * A brewing recipe describing the ingredients (item + count) required to
 * produce a {@link Brew}, along with the minimum cauldron tier and a heat
 * time hint (used by the future BlockEntity-based timed cauldron; the
 * current right-click MVP ignores it).
 *
 * <p>{@code outputBrewId} is stored as a string rather than a resolved
 * {@link Brew} reference because brews can be reloaded independently of
 * recipes (datapack reload order is non-deterministic). Resolution happens
 * via {@link Brews#byId(String)} at consumption time.
 */
public record BrewingRecipe(
        String id,
        List<Ingredient> ingredients,
        CauldronTier cauldronTier,
        int heatTimeTicks,
        String outputBrewId,
        float failureChance,
        java.util.Optional<Catalyst> catalyst) {

    /**
     * Five-argument form for the recipes that predate difficulty.
     *
     * <p>A recipe with no {@code failureChance} cannot fail, which is the right default: most potions
     * in the game are ordinary, and making every existing recipe suddenly risky would be a balance
     * change dressed up as a feature.
     */
    public BrewingRecipe(String id, List<Ingredient> ingredients, CauldronTier cauldronTier,
                         int heatTimeTicks, String outputBrewId) {
        this(id, ingredients, cauldronTier, heatTimeTicks, outputBrewId, 0f, java.util.Optional.empty());
    }

    /**
     * Something that has to go in while the pot is already on the heat.
     *
     * <p>Generalises what the Occamy eggshell does by hand. The window is expressed as a fraction of
     * the brew rather than in ticks, so a recipe's timing survives somebody retuning its
     * {@code heatTimeTicks} — "halfway through" stays halfway through.
     *
     * <p>Adding it outside the window, or never adding it, costs {@code missPenalty}. Nothing here
     * ruins a brew outright: a missed catalyst makes failure <em>likely</em>, and the difference
     * matters because a player who mistimes by a second should feel unlucky rather than robbed.
     *
     * @param item        what to drop in
     * @param windowStart fraction of the brew at which it starts being accepted
     * @param windowEnd   fraction after which it is too late
     * @param missPenalty added to the failure chance when the window closes unsatisfied
     */
    public record Catalyst(Item item, float windowStart, float windowEnd, float missPenalty) {
        public Catalyst {
            Objects.requireNonNull(item, "item");
            if (windowEnd < windowStart) {
                throw new IllegalArgumentException("catalyst window ends before it starts");
            }
        }

        /** Whether {@code progress} (0..1) is inside the window. */
        public boolean acceptsAt(float progress) {
            return progress >= windowStart && progress <= windowEnd;
        }
    }

    public BrewingRecipe {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(ingredients, "ingredients");
        Objects.requireNonNull(cauldronTier, "cauldronTier");
        Objects.requireNonNull(outputBrewId, "outputBrewId");
        if (ingredients.isEmpty()) {
            throw new IllegalArgumentException("BrewingRecipe " + id + " must have at least one ingredient");
        }
        if (heatTimeTicks < 1) {
            throw new IllegalArgumentException("BrewingRecipe " + id + " heatTimeTicks must be >= 1");
        }
        ingredients = List.copyOf(ingredients);
    }

    /** A single required ingredient: item + count. */
    public record Ingredient(Item item, int count) {
        public Ingredient {
            Objects.requireNonNull(item, "item");
            if (count < 1) {
                throw new IllegalArgumentException("ingredient count must be >= 1");
            }
        }
    }

    /**
     * Returns true if {@code container} holds every ingredient (across all slots, summing counts of
     * the same item) and the cauldron tier is sufficient. Pure check — consumes nothing.
     *
     * <p>Takes a {@link Container} rather than a player {@link Inventory} because the ingredients now
     * live in the cauldron. That change is the point of the whole station rewrite: matching against
     * the brewer's backpack meant a cauldron could start a brew out of items that were never in it.
     * {@code Inventory} implements {@code Container}, so the old call sites still compile — but
     * nothing on the cauldron path passes one any more.
     */
    public boolean matches(Container container, CauldronTier presentTier) {
        if (!presentTier.isAtLeast(cauldronTier)) return false;
        Map<Item, Integer> available = countByItem(container);
        for (Ingredient ing : ingredients) {
            int have = available.getOrDefault(ing.item(), 0);
            if (have < ing.count()) return false;
        }
        return true;
    }

    /**
     * Consumes the recipe's ingredients from {@code container}. Caller must have already confirmed
     * {@link #matches(Container, CauldronTier)}.
     */
    public void consumeFrom(Container container) {
        for (Ingredient ing : ingredients) {
            int remaining = ing.count();
            for (int slot = 0; slot < container.getContainerSize() && remaining > 0; slot++) {
                ItemStack stack = container.getItem(slot);
                if (!stack.isEmpty() && stack.is(ing.item())) {
                    int take = Math.min(stack.getCount(), remaining);
                    // removeItem rather than shrink: a SimpleContainer built from a copied array
                    // hands out live stacks, but a Container implementation is entitled not to, and
                    // shrinking a copy would silently consume nothing.
                    container.removeItem(slot, take);
                    remaining -= take;
                }
            }
        }
        container.setChanged();
    }

    private static Map<Item, Integer> countByItem(Container container) {
        Map<Item, Integer> totals = new HashMap<>();
        for (int slot = 0; slot < container.getContainerSize(); slot++) {
            ItemStack stack = container.getItem(slot);
            if (stack.isEmpty()) continue;
            totals.merge(stack.getItem(), stack.getCount(), Integer::sum);
        }
        return Collections.unmodifiableMap(totals);
    }
}
