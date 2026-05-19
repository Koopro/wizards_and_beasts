package at.koopro.wizardsandbeasts.broom;

import at.koopro.wizardsandbeasts.event.broom.BroomDefinitionsLoadedEvent;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.neoforged.neoforge.common.NeoForge;

import java.util.HashMap;
import java.util.Map;

public final class BroomDefinitionLoader extends SimpleJsonResourceReloadListener<BroomDefinition> {
    public static final String DIRECTORY = "broom_definitions";

    public BroomDefinitionLoader() {
        super(BroomDefinition.CODEC, FileToIdConverter.json(DIRECTORY));
    }

    @Override
    protected void apply(Map<Identifier, BroomDefinition> jsonEntries, ResourceManager resourceManager, ProfilerFiller profiler) {
        Map<Identifier, BroomDefinition> loaded = new HashMap<>(jsonEntries);
        BroomDefinitionRegistry.replaceAll(loaded);
        NeoForge.EVENT_BUS.post(new BroomDefinitionsLoadedEvent(loaded.size()));
    }
}
