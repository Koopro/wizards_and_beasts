package at.koopro.wizardsandbeasts.brew;

import com.mojang.logging.LogUtils;
import net.minecraft.world.entity.player.Inventory;
import org.slf4j.Logger;

import org.jspecify.annotations.Nullable;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * In-memory registry of {@link BrewingRecipe}s. Populated by
 * {@link at.koopro.wizardsandbeasts.brew.def.BrewingRecipeReloadListener} on every
 * datapack reload, plus optional {@link #register(BrewingRecipe)} calls
 * from addon mods via
 * {@link at.koopro.wizardsandbeasts.event.RegisterBrewsEvent}.
 *
 * <p>Recipe matching is intentionally O(n) over recipes — n is small in
 * practice (dozens, not thousands) and inventory diffs aren't on the hot
 * path. A future tier index keyed on cauldron tier can be added if profiling
 * shows it matters.
 */
public final class BrewingRecipes {

    private static final Logger LOGGER = LogUtils.getLogger();

    private static final Map<String, BrewingRecipe> BY_ID = new LinkedHashMap<>();

    private BrewingRecipes() {}

    public static BrewingRecipe register(BrewingRecipe recipe) {
        if (BY_ID.containsKey(recipe.id())) {
            LOGGER.warn("BrewingRecipe id collision: '{}' is already registered; replacing.", recipe.id());
        }
        BY_ID.put(recipe.id(), recipe);
        return recipe;
    }

    public static void clear() {
        BY_ID.clear();
    }

    @Nullable
    public static BrewingRecipe byId(String id) {
        return id == null ? null : BY_ID.get(id);
    }

    public static Collection<BrewingRecipe> all() {
        return Collections.unmodifiableCollection(BY_ID.values());
    }

    public static int count() {
        return BY_ID.size();
    }

    /**
     * First registered recipe whose ingredients are present in {@code inventory}
     * and whose required cauldron tier is met by {@code presentTier}; null if
     * none. Iteration order matches registration order, which for JSON spells
     * matches the datapack load order — so player-defined recipes can override
     * mod-supplied ones by sharing an id.
     */
    @Nullable
    public static BrewingRecipe findMatch(Inventory inventory, CauldronTier presentTier) {
        for (BrewingRecipe recipe : BY_ID.values()) {
            if (recipe.matches(inventory, presentTier)) return recipe;
        }
        return null;
    }
}
