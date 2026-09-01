package at.koopro.wizardsandbeasts.bestiary;

import at.koopro.wizardsandbeasts.event.bestiary.BestiaryEntriesLoadedEvent;
import com.mojang.logging.LogUtils;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;

import java.util.HashMap;
import java.util.Map;

public final class BestiaryEntryLoader extends SimpleJsonResourceReloadListener<BestiaryEntry> {
    public static final String DIRECTORY = "bestiary/entries";
    public BestiaryEntryLoader() { super(BestiaryEntry.CODEC, FileToIdConverter.json(DIRECTORY)); }

    private static final Logger LOGGER = LogUtils.getLogger();

    @Override
    protected void apply(Map<Identifier, BestiaryEntry> jsonEntries, ResourceManager resourceManager, ProfilerFiller profiler) {
        Map<Identifier, BestiaryEntry> loaded = new HashMap<>(jsonEntries);
        BestiaryEntryRegistry.replaceAll(loaded);
        // Logged like every other loader in the mod. Without it a boot cannot be read for whether the
        // book has any pages at all, which is exactly the state KNOWN_ISSUES.md recorded wrongly.
        long discoverable = loaded.values().stream().filter(e -> e.entityType().isPresent()).count();
        LOGGER.info("Loaded {} bestiary entrie(s), {} of them tied to an entity.",
                loaded.size(), discoverable);
        NeoForge.EVENT_BUS.post(new BestiaryEntriesLoadedEvent(loaded.size()));
    }
}
