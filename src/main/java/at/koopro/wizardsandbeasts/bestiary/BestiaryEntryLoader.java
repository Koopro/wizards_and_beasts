package at.koopro.wizardsandbeasts.bestiary;

import at.koopro.wizardsandbeasts.event.bestiary.BestiaryEntriesLoadedEvent;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.neoforged.neoforge.common.NeoForge;

import java.util.HashMap;
import java.util.Map;

public final class BestiaryEntryLoader extends SimpleJsonResourceReloadListener<BestiaryEntry> {
    public static final String DIRECTORY = "bestiary/entries";
    public BestiaryEntryLoader() { super(BestiaryEntry.CODEC, FileToIdConverter.json(DIRECTORY)); }

    @Override
    protected void apply(Map<Identifier, BestiaryEntry> jsonEntries, ResourceManager resourceManager, ProfilerFiller profiler) {
        Map<Identifier, BestiaryEntry> loaded = new HashMap<>(jsonEntries);
        BestiaryEntryRegistry.replaceAll(loaded);
        NeoForge.EVENT_BUS.post(new BestiaryEntriesLoadedEvent(loaded.size()));
    }
}
