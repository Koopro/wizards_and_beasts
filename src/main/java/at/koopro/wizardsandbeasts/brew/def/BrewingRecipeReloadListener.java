package at.koopro.wizardsandbeasts.brew.def;

import at.koopro.wizardsandbeasts.brew.BrewingRecipe;
import at.koopro.wizardsandbeasts.brew.BrewingRecipes;
import com.mojang.logging.LogUtils;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import org.slf4j.Logger;

import java.util.Map;

/**
 * Loads {@link BrewingRecipeDefinition}s from
 * {@code data/<ns>/<modId>/brewing_recipes/*.json} on every datapack reload.
 *
 * <p>Cross-references to brews are resolved lazily by id at consumption time
 * (see {@link BrewingRecipe#outputBrewId()}), so brew-vs-recipe load order
 * doesn't matter — and a recipe whose output id is missing simply won't
 * produce a usable brew (logged on consume rather than on reload).
 */
public class BrewingRecipeReloadListener extends SimpleJsonResourceReloadListener<BrewingRecipeDefinition> {

    private static final Logger LOGGER = LogUtils.getLogger();

    // Un-prefixed like every other listener; the MODID-prefixed value never matched the
    // shipped data path, so datapack brewing recipes silently never loaded (AUD-D-002).
    public static final String DIRECTORY = "brewing_recipes";

    public BrewingRecipeReloadListener() {
        super(BrewingRecipeDefinition.CODEC, FileToIdConverter.json(DIRECTORY));
    }

    @Override
    protected void apply(Map<Identifier, BrewingRecipeDefinition> map,
                         ResourceManager resourceManager,
                         ProfilerFiller profiler) {
        BrewingRecipes.clear();

        int loaded = 0;
        int failed = 0;
        for (Map.Entry<Identifier, BrewingRecipeDefinition> entry : map.entrySet()) {
            Identifier id = entry.getKey();
            String fullId = id.getNamespace() + ":" + id.getPath();
            try {
                BrewingRecipe recipe = entry.getValue().toRecipe(fullId);
                if (recipe == null) {
                    LOGGER.warn("BrewingRecipeDefinition '{}' has no resolvable ingredients; skipping.", fullId);
                    failed++;
                    continue;
                }
                BrewingRecipes.register(recipe);
                loaded++;
            } catch (RuntimeException ex) {
                LOGGER.warn("Failed to register BrewingRecipe '{}': {}", fullId, ex.toString());
                failed++;
            }
        }
        LOGGER.info("BrewingRecipeReloadListener: loaded {} recipes ({} failed).", loaded, failed);
    }
}
