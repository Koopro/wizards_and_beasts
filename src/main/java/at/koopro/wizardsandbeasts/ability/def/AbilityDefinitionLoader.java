package at.koopro.wizardsandbeasts.ability.def;

import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import org.jspecify.annotations.NullMarked;

import java.util.HashMap;
import java.util.Map;

/**
 * Datapack reload listener for {@link AbilityDefinition}s, mirroring {@code BroomDefinitionLoader}. Reads
 * {@code data/<ns>/abilities/*.json}; the file {@link Identifier} becomes the definition id (JSON body omits
 * it), stamped via {@link AbilityDefinition#withId}. Registered in {@code WizardsAndBeastsMod} on the
 * {@code AddServerReloadListenersEvent}. Client mirrors are pushed separately on {@code OnDatapackSyncEvent}.
 */
@NullMarked
public final class AbilityDefinitionLoader extends SimpleJsonResourceReloadListener<AbilityDefinition> {

    public static final String DIRECTORY = "abilities";

    public AbilityDefinitionLoader() {
        super(AbilityDefinition.CODEC, FileToIdConverter.json(DIRECTORY));
    }

    @Override
    protected void apply(Map<Identifier, AbilityDefinition> jsonEntries,
                         ResourceManager resourceManager,
                         ProfilerFiller profiler) {
        Map<Identifier, AbilityDefinition> loaded = new HashMap<>(jsonEntries.size());
        for (Map.Entry<Identifier, AbilityDefinition> entry : jsonEntries.entrySet()) {
            loaded.put(entry.getKey(), entry.getValue().withId(entry.getKey()));
        }
        AbilityDefinitionRegistry.replaceAll(loaded);
    }
}
