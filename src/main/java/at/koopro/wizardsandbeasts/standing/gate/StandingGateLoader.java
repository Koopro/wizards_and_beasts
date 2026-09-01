package at.koopro.wizardsandbeasts.standing.gate;

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
 * Loads {@code data/<namespace>/standing_gates/*.json} into {@link StandingGates}.
 *
 * <p>Only logs when gates are actually present. A default install has none, and a line saying
 * "loaded 0 standing gates" on every reload would be noise on the one boot log people read.
 */
@NullMarked
public final class StandingGateLoader extends SimpleJsonResourceReloadListener<StandingGate> {

    private static final Logger LOGGER = LogUtils.getLogger();

    public static final String DIRECTORY = "standing_gates";

    public StandingGateLoader() {
        super(StandingGate.CODEC, FileToIdConverter.json(DIRECTORY));
    }

    @Override
    protected void apply(Map<Identifier, StandingGate> jsonEntries,
                         ResourceManager resourceManager,
                         ProfilerFiller profiler) {
        StandingGates.replaceAll(new HashMap<>(jsonEntries));
        if (!StandingGates.isEmpty()) {
            LOGGER.info("[W&B] Loaded {} standing gates over the skill web.", StandingGates.count());
        }
    }
}
