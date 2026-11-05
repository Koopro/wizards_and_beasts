package at.koopro.wizardsandbeasts.creature.ecology;

import com.google.gson.JsonElement;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.DataResult;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.util.profiling.ProfilerFiller;
import org.jspecify.annotations.NullMarked;
import org.slf4j.Logger;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Reload listener for {@code data/wizards_and_beasts/creature_ecology/*.json}. The file name is the creature id,
 * as for {@code creature_bonds}: the ecology of bespoke Java creatures (Niffler, Mooncalf, Bowtruckle…) and
 * datapack ones lives in one place. Files under {@code _archetypes/} are bases other files extend
 * ({@link EcologyProfileFiles}); they are not creatures. A file that fails to parse is logged and skipped, the rest
 * load.
 */
@NullMarked
public final class EcologyProfileLoader extends SimpleJsonResourceReloadListener<JsonElement> {

    public static final String DIRECTORY = "creature_ecology";
    private static final Logger LOGGER = LogUtils.getLogger();

    public EcologyProfileLoader() {
        super(ExtraCodecs.JSON, FileToIdConverter.json(DIRECTORY));
    }

    @Override
    protected void apply(Map<Identifier, JsonElement> jsonEntries, ResourceManager resourceManager,
                         ProfilerFiller profiler) {
        // Archetypes are shared across namespaces by path; a datapack may add its own or override ours.
        Map<String, JsonElement> byPath = new LinkedHashMap<>();
        Map<String, String> namespaceOf = new HashMap<>();
        jsonEntries.forEach((id, json) -> {
            byPath.put(id.getPath(), json);
            namespaceOf.put(id.getPath(), id.getNamespace());
        });
        Map<Identifier, EcologyProfile> loaded = new HashMap<>();
        for (Map.Entry<String, DataResult<EcologyProfile>> e : EcologyProfileFiles.resolve(byPath).entrySet()) {
            Identifier id = Identifier.fromNamespaceAndPath(namespaceOf.get(e.getKey()), e.getKey());
            e.getValue().ifSuccess(profile -> loaded.put(id, profile))
                    .ifError(error -> LOGGER.error("Creature ecology {} skipped: {}", id, error.message()));
        }
        EcologyProfileRegistry.replaceAll(loaded);
        LOGGER.info("Loaded {} creature ecology profile(s).", loaded.size());
    }
}
