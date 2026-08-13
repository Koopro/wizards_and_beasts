package at.koopro.wizardsandbeasts.heritage.appearance;

import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;

import java.util.HashMap;
import java.util.Map;

/**
 * Loads {@code data/&lt;namespace&gt;/heritage_appearance/*.json} into
 * {@link HeritageAppearanceRegistry}, mirroring {@code BestiaryEntryLoader}.
 *
 * <p>Wired in from the mod's {@code AddServerReloadListenersEvent} handler. Entries are re-synced to
 * every client on {@code /reload} by {@code SyncHeritageAppearancePayload}, which listens to
 * {@code OnDatapackSyncEvent}.
 */
public final class HeritageAppearanceLoader extends SimpleJsonResourceReloadListener<HeritageAppearance> {

    public static final String DIRECTORY = "heritage_appearance";

    public HeritageAppearanceLoader() {
        super(HeritageAppearance.CODEC, FileToIdConverter.json(DIRECTORY));
    }

    @Override
    protected void apply(Map<Identifier, HeritageAppearance> jsonEntries,
                         ResourceManager resourceManager,
                         ProfilerFiller profiler) {
        HeritageAppearanceRegistry.replaceAll(new HashMap<>(jsonEntries));
    }
}
