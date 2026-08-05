package at.koopro.wizardsandbeasts.animagus;

import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import org.jspecify.annotations.NullMarked;

import java.util.HashMap;
import java.util.Map;

/**
 * Datapack reload listener for Animagus form definitions, mirroring {@code BestiaryEntryLoader}.
 * Wired in from the mod's {@code AddReloadListenerEvent} handler.
 */
@NullMarked
public final class AnimagusFormLoader extends SimpleJsonResourceReloadListener<AnimagusFormDefinition> {

    public static final String DIRECTORY = "animagus_forms";

    public AnimagusFormLoader() {
        super(AnimagusFormDefinition.CODEC, FileToIdConverter.json(DIRECTORY));
    }

    @Override
    protected void apply(Map<Identifier, AnimagusFormDefinition> jsonEntries,
                         ResourceManager resourceManager,
                         ProfilerFiller profiler) {
        AnimagusFormRegistry.replaceAll(new HashMap<>(jsonEntries));
    }
}
