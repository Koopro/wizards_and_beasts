package at.koopro.wizardsandbeasts.artefact.erised;

import com.mojang.logging.LogUtils;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import org.jspecify.annotations.NullMarked;
import org.slf4j.Logger;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** The loaded vision table, by file id: {@code erised_visions/giant.json} is {@code wizards_and_beasts:giant}. */
@NullMarked
public final class ErisedVisions {

    public static final String DIRECTORY = "erised_visions";
    private static final Logger LOGGER = LogUtils.getLogger();

    private static volatile Map<Identifier, ErisedVision> visions = Map.of();

    private ErisedVisions() {}

    public static Map<Identifier, ErisedVision> all() {
        return visions;
    }

    /** Replaces the table at once (reload, tests). Sorted by id, so selection does not depend on load order. */
    public static void replaceAll(Map<Identifier, ErisedVision> loaded) {
        Map<Identifier, ErisedVision> sorted = new LinkedHashMap<>();
        loaded.entrySet().stream().sorted(Map.Entry.comparingByKey())
                .forEach(e -> sorted.put(e.getKey(), e.getValue()));
        visions = Collections.unmodifiableMap(sorted);
    }

    public static final class Loader extends SimpleJsonResourceReloadListener<ErisedVision> {

        public Loader() {
            super(ErisedVision.CODEC, FileToIdConverter.json(DIRECTORY));
        }

        @Override
        protected void apply(Map<Identifier, ErisedVision> entries, ResourceManager resourceManager,
                             ProfilerFiller profiler) {
            replaceAll(entries);
            LOGGER.info("[Erised] Loaded {} vision(s)", entries.size());
        }
    }
}
