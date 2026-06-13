package at.koopro.wizardsandbeasts.spell.def;

import at.koopro.wizardsandbeasts.spell.core.JsonSpell;
import at.koopro.wizardsandbeasts.spell.core.Spells;
import com.mojang.logging.LogUtils;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import org.slf4j.Logger;

import java.util.Map;

/**
 * Loads {@link SpellDefinition}s from {@code data/<namespace>/spells/*.json}
 * on every datapack reload, registering each as a {@link JsonSpell}. Java
 * spells are not touched; only the JSON-contributed slice of the registry is
 * cleared and rebuilt per reload, so this is safe to invoke at runtime.
 *
 * <p>Wire it into NeoForge by subscribing
 * {@code AddReloadListenerEvent} and calling {@code event.addListener(new SpellReloadListener())}.
 *
 * <p>The base class parses each JSON file via {@link SpellDefinition#CODEC} on
 * the worker thread (in {@code prepare()}) and hands us a fully-decoded
 * {@code Map<Identifier, SpellDefinition>} on the main thread, so {@link #apply}
 * only deals with registry mutation.
 */
public class SpellReloadListener extends SimpleJsonResourceReloadListener<SpellDefinition> {

    private static final Logger LOGGER = LogUtils.getLogger();

    /** Datapack folder under {@code data/<ns>/<DIRECTORY>/...}. */
    public static final String DIRECTORY = "spells";

    public SpellReloadListener() {
        super(SpellDefinition.CODEC, FileToIdConverter.json(DIRECTORY));
    }

    @Override
    protected void apply(Map<Identifier, SpellDefinition> map,
                         ResourceManager resourceManager,
                         ProfilerFiller profiler) {
        Spells.clearJsonSpells();

        int loaded = 0;
        int failed = 0;

        for (Map.Entry<Identifier, SpellDefinition> entry : map.entrySet()) {
            Identifier id = entry.getKey();
            SpellDefinition def = entry.getValue();
            String fullId = id.getNamespace() + ":" + id.getPath();
            try {
                Spells.registerJson(new JsonSpell(fullId, def));
                loaded++;
            } catch (RuntimeException ex) {
                LOGGER.warn("Failed to register JsonSpell '{}': {}", fullId, ex.toString());
                failed++;
            }
        }

        LOGGER.info("SpellReloadListener: loaded {} JSON spells ({} failed).", loaded, failed);
    }
}
