package at.koopro.wizardsandbeasts.creature;

import com.mojang.logging.LogUtils;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import org.slf4j.Logger;

import java.util.HashMap;
import java.util.Map;

/** Reload listener for {@code data/wizards_and_beasts/creatures/*.json}, mirroring {@code BroomDefinitionLoader}. */
public final class CreatureDefinitionLoader extends SimpleJsonResourceReloadListener<CreatureDefinition> {
    public static final String DIRECTORY = "creatures";
    private static final Logger LOGGER = LogUtils.getLogger();

    public CreatureDefinitionLoader() {
        super(CreatureDefinition.CODEC, FileToIdConverter.json(DIRECTORY));
    }

    @Override
    protected void apply(Map<Identifier, CreatureDefinition> jsonEntries, ResourceManager resourceManager, ProfilerFiller profiler) {
        Map<Identifier, CreatureDefinition> loaded = new HashMap<>(jsonEntries);
        CreatureDefinitionRegistry.replaceAll(loaded);
        LOGGER.info("Loaded {} creature definition(s).", loaded.size());
    }
}
