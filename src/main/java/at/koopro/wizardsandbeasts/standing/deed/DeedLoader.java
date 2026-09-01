package at.koopro.wizardsandbeasts.standing.deed;

import com.mojang.logging.LogUtils;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import org.jspecify.annotations.NullMarked;
import org.slf4j.Logger;

import java.util.HashMap;
import java.util.Map;

/**
 * Loads {@code data/<namespace>/magical_deeds/*.json} into {@link DeedRegistry}, mirroring
 * {@code HeritageAppearanceLoader}. Wired in from the mod's {@code AddServerReloadListenersEvent}
 * handler.
 *
 * <p>Logs the count on every reload. Deeds are invisible by nature — a rule that fires correctly
 * produces a number moving slightly on a screen nobody has open — so the load line is the only cheap
 * way to tell "no deeds authored" apart from "deeds authored and not firing".
 */
@NullMarked
public final class DeedLoader extends SimpleJsonResourceReloadListener<Deed> {

    private static final Logger LOGGER = LogUtils.getLogger();

    public static final String DIRECTORY = "magical_deeds";

    public DeedLoader() {
        super(Deed.CODEC, FileToIdConverter.json(DIRECTORY));
    }

    @Override
    protected void apply(Map<Identifier, Deed> jsonEntries,
                         ResourceManager resourceManager,
                         ProfilerFiller profiler) {
        DeedRegistry.replaceAll(new HashMap<>(jsonEntries));
        LOGGER.info("[W&B] Loaded {} magical deeds.", DeedRegistry.count());
    }
}
