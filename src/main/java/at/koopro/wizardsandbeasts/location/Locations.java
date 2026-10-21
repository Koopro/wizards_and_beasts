package at.koopro.wizardsandbeasts.location;

import at.koopro.wizardsandbeasts.module.ModuleManager;
import com.mojang.logging.LogUtils;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The loaded locations, by file id: {@code locations/gringotts.json} is {@code wizards_and_beasts:gringotts}. A file
 * that fails {@link LocationDefinition#validate()} is refused with a log line, so a typo in a tier id is a load
 * error rather than a service no one can ever reach.
 */
@NullMarked
public final class Locations {

    public static final String DIRECTORY = "locations";
    private static final Logger LOGGER = LogUtils.getLogger();

    private static volatile Map<Identifier, LocationDefinition> locations = Map.of();

    private Locations() {}

    /** Every loaded location, whatever its module's state. */
    public static Map<Identifier, LocationDefinition> all() {
        return locations;
    }

    public static @Nullable LocationDefinition get(Identifier id) {
        return locations.get(id);
    }

    /** The location, if loaded and its module is on. */
    public static @Nullable LocationDefinition open(Identifier id) {
        LocationDefinition def = locations.get(id);
        return def != null && ModuleManager.isEnabled(def.module()) ? def : null;
    }

    /** Replaces every location at once (reload, tests). Insertion order is kept for stable iteration. */
    public static void replaceAll(Map<Identifier, LocationDefinition> loaded) {
        locations = java.util.Collections.unmodifiableMap(new LinkedHashMap<>(loaded));
    }

    public static final class Loader extends SimpleJsonResourceReloadListener<LocationDefinition> {

        public Loader() {
            super(LocationDefinition.CODEC, FileToIdConverter.json(DIRECTORY));
        }

        @Override
        protected void apply(Map<Identifier, LocationDefinition> entries, ResourceManager resourceManager,
                             ProfilerFiller profiler) {
            Map<Identifier, LocationDefinition> loaded = new LinkedHashMap<>();
            entries.entrySet().stream()
                    .sorted(Map.Entry.comparingByKey())
                    .forEach(entry -> {
                        List<String> problems = new ArrayList<>(entry.getValue().validate());
                        for (ServiceOffer offer : entry.getValue().services()) {
                            if (LocationServices.get(offer.service()) == null) {
                                problems.add("unknown service " + offer.service());
                            }
                        }
                        if (!problems.isEmpty()) {
                            LOGGER.error("[Location] {} refused: {}", entry.getKey(), problems);
                            return;
                        }
                        loaded.put(entry.getKey(), entry.getValue());
                    });
            replaceAll(loaded);
            LOGGER.info("[Location] Loaded {} location(s): {}", loaded.size(), loaded.keySet());
        }
    }
}
