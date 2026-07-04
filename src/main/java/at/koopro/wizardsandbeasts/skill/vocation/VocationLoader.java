package at.koopro.wizardsandbeasts.skill.vocation;

import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import org.jspecify.annotations.NullMarked;

import java.util.HashMap;
import java.util.Map;

/**
 * Datapack reload listener for Vocation definitions, mirroring {@code BestiaryEntryLoader}. Wired into
 * {@code AddServerReloadListenersEvent} in {@code WizardsAndBeastsMod}.
 */
@NullMarked
public final class VocationLoader extends SimpleJsonResourceReloadListener<VocationDefinition> {
    public static final String DIRECTORY = "vocations";

    public VocationLoader() {
        super(VocationDefinition.CODEC, FileToIdConverter.json(DIRECTORY));
    }

    @Override
    protected void apply(Map<Identifier, VocationDefinition> jsonEntries, ResourceManager resourceManager,
                         ProfilerFiller profiler) {
        VocationRegistry.replaceAll(new HashMap<>(jsonEntries));
    }
}
