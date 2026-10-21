package at.koopro.wizardsandbeasts.treasure;

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
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** The loaded treasure sites, by file id, with the reverse index the loot modifier reads per chest. */
@NullMarked
public final class TreasureSites {

    public static final String DIRECTORY = "treasure_sites";
    private static final Logger LOGGER = LogUtils.getLogger();

    private record Index(Map<Identifier, TreasureSite> sites, Map<Identifier, List<Identifier>> byLootTable) {}

    private static volatile Index index = new Index(Map.of(), Map.of());

    private TreasureSites() {}

    public static @Nullable TreasureSite get(Identifier id) {
        return index.sites().get(id);
    }

    public static Map<Identifier, TreasureSite> all() {
        return index.sites();
    }

    /** The sites whose chests are this loot table; empty for any other table. */
    public static List<Identifier> forLootTable(Identifier lootTable) {
        return index.byLootTable().getOrDefault(lootTable, List.of());
    }

    public static void replaceAll(Map<Identifier, TreasureSite> loaded) {
        Map<Identifier, List<Identifier>> byTable = new HashMap<>();
        loaded.forEach((id, site) -> site.lootTables().forEach(table ->
                byTable.computeIfAbsent(table, t -> new ArrayList<>()).add(id)));
        byTable.replaceAll((table, ids) -> List.copyOf(ids));
        index = new Index(java.util.Collections.unmodifiableMap(new LinkedHashMap<>(loaded)), Map.copyOf(byTable));
    }

    public static final class Loader extends SimpleJsonResourceReloadListener<TreasureSite> {

        public Loader() {
            super(TreasureSite.CODEC, FileToIdConverter.json(DIRECTORY));
        }

        @Override
        protected void apply(Map<Identifier, TreasureSite> entries, ResourceManager resourceManager,
                             ProfilerFiller profiler) {
            Map<Identifier, TreasureSite> loaded = new LinkedHashMap<>();
            entries.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> {
                List<String> problems = entry.getValue().validate();
                if (!problems.isEmpty()) {
                    LOGGER.error("[Treasure] site {} refused: {}", entry.getKey(), problems);
                    return;
                }
                loaded.put(entry.getKey(), entry.getValue());
            });
            replaceAll(loaded);
            LOGGER.info("[Treasure] Loaded {} treasure site(s): {}", loaded.size(), loaded.keySet());
        }
    }
}
