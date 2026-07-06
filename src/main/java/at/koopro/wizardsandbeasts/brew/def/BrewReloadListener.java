package at.koopro.wizardsandbeasts.brew.def;

import at.koopro.wizardsandbeasts.brew.Brew;
import at.koopro.wizardsandbeasts.brew.Brews;
import com.mojang.logging.LogUtils;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import org.slf4j.Logger;

import java.util.Map;

/**
 * Loads {@link BrewDefinition}s from {@code data/<ns>/brews/*.json} on
 * every datapack reload. Mirrors
 * {@link at.koopro.wizardsandbeasts.spell.def.SpellReloadListener}.
 *
 * <p>Reload semantics: every reload wipes the entire registry and rebuilds
 * it. There is no "Java vs JSON" split for brews because the brewing pillar
 * is data-driven from day one. Addon mods contribute via
 * {@link at.koopro.wizardsandbeasts.event.RegisterBrewsEvent}, which fires after this
 * listener so addon brews always win the second pass on reload.
 */
public class BrewReloadListener extends SimpleJsonResourceReloadListener<BrewDefinition> {

    private static final Logger LOGGER = LogUtils.getLogger();

    // Un-prefixed like every other listener (spells, creatures, broom_definitions, …).
    // The old MODID-prefixed value scanned data/<ns>/wizards_and_beasts/brews/ — a dir that
    // never existed, so datapack brews silently never loaded (AUD-D-002).
    public static final String DIRECTORY = "brews";

    public BrewReloadListener() {
        super(BrewDefinition.CODEC, FileToIdConverter.json(DIRECTORY));
    }

    @Override
    protected void apply(Map<Identifier, BrewDefinition> map,
                         ResourceManager resourceManager,
                         ProfilerFiller profiler) {
        Brews.clear();

        int loaded = 0;
        int failed = 0;
        for (Map.Entry<Identifier, BrewDefinition> entry : map.entrySet()) {
            Identifier id = entry.getKey();
            String fullId = id.getNamespace() + ":" + id.getPath();
            Brew brew = entry.getValue().toBrew(fullId);
            if (brew == null) {
                LOGGER.warn("BrewDefinition '{}' produced no usable effects; skipping.", fullId);
                failed++;
                continue;
            }
            Brews.register(brew);
            loaded++;
        }
        LOGGER.info("BrewReloadListener: loaded {} brews ({} failed).", loaded, failed);
    }
}
