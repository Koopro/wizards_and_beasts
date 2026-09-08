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

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
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

    /** Last accepted definitions, kept for the client sync. Published as an immutable map. */
    private static volatile Map<Identifier, SpellDefinition> LOADED = Map.of();

    public SpellReloadListener() {
        super(SpellDefinition.CODEC, FileToIdConverter.json(DIRECTORY));
    }

    @Override
    protected void apply(Map<Identifier, SpellDefinition> map,
                         ResourceManager resourceManager,
                         ProfilerFiller profiler) {
        // Sorted, so the registry's iteration order — which drives the spell menu's grouping and
        // every command suggestion list — is the same on every load rather than whatever order the
        // resource manager happened to hand back.
        List<Identifier> ids = new ArrayList<>(map.keySet());
        ids.sort(Comparator.comparing(Identifier::toString));

        Map<Identifier, SpellDefinition> accepted = new LinkedHashMap<>(ids.size());
        List<JsonSpell> spells = new ArrayList<>(ids.size());
        int failed = 0;

        for (Identifier id : ids) {
            SpellDefinition def = map.get(id);
            String fullId = id.getNamespace() + ":" + id.getPath();
            try {
                spells.add(new JsonSpell(fullId, def));
                accepted.put(id, def);
            } catch (RuntimeException ex) {
                LOGGER.warn("Failed to register JsonSpell '{}': {}", fullId, ex.toString());
                failed++;
            }
        }

        // One publish for the whole slice — see Spells.replaceJsonSpells for why the old
        // clear-then-register-each sweep left the registry observably incomplete in between.
        Spells.replaceJsonSpells(spells);
        // Kept verbatim so the same table can be pushed to clients on login and on /reload without
        // re-reading the resource manager. See SpellDefinitionsSyncS2CPayload.
        LOADED = Collections.unmodifiableMap(accepted);

        LOGGER.info("SpellReloadListener: loaded {} JSON spells ({} failed).", spells.size(), failed);
    }

    /**
     * The definitions this listener last accepted, in registry order.
     *
     * <p>Server-side only, and read by the sync payload rather than re-derived: a client mirror built
     * from anything other than the exact map the server registered is a second source of truth, and
     * the two would drift the first time a load error was handled differently on either side.
     */
    public static Map<Identifier, SpellDefinition> loaded() {
        return LOADED;
    }
}
