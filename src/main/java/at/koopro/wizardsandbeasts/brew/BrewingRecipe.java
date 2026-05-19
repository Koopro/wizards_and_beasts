package at.koopro.wizardsandbeasts.brew;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
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
        String outputBrewId) {

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
     * Returns true if {@code inventory} contains every ingredient (across all
     * slots, summing counts of the same item) and the cauldron tier is
     * sufficient. Pure check — does not consume anything.
     */
    public boolean matches(Inventory inventory, CauldronTier presentTier) {
        if (!presentTier.isAtLeast(cauldronTier)) return false;
        Map<Item, Integer> available = countByItem(inventory);
        for (Ingredient ing : ingredients) {
            int have = available.getOrDefault(ing.item(), 0);
            if (have < ing.count()) return false;
        }
        return true;
    }

    /**
     * Consumes the recipe's ingredients from {@code inventory}. Caller must
     * have already confirmed {@link #matches(Inventory, CauldronTier)}.
     */
    public void consumeFrom(Inventory inventory) {
        for (Ingredient ing : ingredients) {
            int remaining = ing.count();
            for (int slot = 0; slot < inventory.getContainerSize() && remaining > 0; slot++) {
                ItemStack stack = inventory.getItem(slot);
                if (!stack.isEmpty() && stack.is(ing.item())) {
                    int take = Math.min(stack.getCount(), remaining);
                    stack.shrink(take);
                    remaining -= take;
                }
            }
        }
    }

    private static Map<Item, Integer> countByItem(Inventory inventory) {
        Map<Item, Integer> totals = new HashMap<>();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack.isEmpty()) continue;
            totals.merge(stack.getItem(), stack.getCount(), Integer::sum);
        }
        return Collections.unmodifiableMap(totals);
    }
}
