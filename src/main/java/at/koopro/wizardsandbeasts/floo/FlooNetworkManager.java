package at.koopro.wizardsandbeasts.floo;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.registry.ModAttachments;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

public final class FlooNetworkManager extends SavedData {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int MAX_LOG_ENTRIES = 100;

    public static final SavedDataType<FlooNetworkManager> TYPE = new SavedDataType<>(
            WizardsAndBeastsMod.MODID + "_floo_network",
            FlooNetworkManager::new,
            RecordCodecBuilder.create(instance -> instance.group(
                    Codec.unboundedMap(Codec.STRING, FlooRegistryEntry.CODEC)
                            .optionalFieldOf("entries", Map.of())
                            .forGetter(d -> Map.copyOf(d.entries)),
                    Codec.STRING.listOf()
                            .optionalFieldOf("travelLog", List.of())
                            .forGetter(d -> List.copyOf(d.travelLog))
            ).apply(instance, FlooNetworkManager::ofCodec)));

    private final LinkedHashMap<String, FlooRegistryEntry> entries;
    private final LinkedList<String> travelLog;

    public FlooNetworkManager() {
        this(new LinkedHashMap<>(), new LinkedList<>());
    }

    private FlooNetworkManager(@NonNull LinkedHashMap<String, FlooRegistryEntry> entries,
                                @NonNull LinkedList<String> travelLog) {
        this.entries = entries;
        this.travelLog = travelLog;
    }

    private static FlooNetworkManager ofCodec(@NonNull Map<String, FlooRegistryEntry> entries,
                                               @NonNull List<String> log) {
        return new FlooNetworkManager(new LinkedHashMap<>(entries), new LinkedList<>(log));
    }

    public static FlooNetworkManager get(@NonNull ServerLevel level) {
        return level.getServer().overworld().getDataStorage().computeIfAbsent(TYPE);
    }

    public boolean register(@NonNull String address, @NonNull Identifier dimension,
                             @NonNull BlockPos pos, boolean isPublic) {
        String key = address.strip().toLowerCase(Locale.ROOT);
        if (entries.containsKey(key)) {
            LOGGER.warn("[FlooNetwork] Address already taken: {}", key);
            return false;
        }
        entries.put(key, new FlooRegistryEntry(address.strip(), dimension, pos, true, isPublic));
        setDirty();
        return true;
    }

    public void unregister(@NonNull String address) {
        String key = address.strip().toLowerCase(Locale.ROOT);
        if (entries.remove(key) != null) {
            setDirty();
        }
    }

    @Nullable
    public FlooRegistryEntry getEntry(@NonNull String address) {
        return entries.get(address.strip().toLowerCase(Locale.ROOT));
    }

    @NonNull
    public List<FlooRegistryEntry> getDestinations(@NonNull Player player) {
        Set<String> visited = player.getData(ModAttachments.FLOO_VISITED_DESTINATIONS.get());
        return entries.values().stream()
                .filter(e -> e.isPublic() || visited.contains(e.networkAddress().toLowerCase(Locale.ROOT)))
                .sorted(Comparator.comparing(e -> e.networkAddress().toLowerCase(Locale.ROOT)))
                .collect(Collectors.toList());
    }

    @NonNull
    public List<FlooRegistryEntry> getAllEntries() {
        return new ArrayList<>(entries.values());
    }

    @NonNull
    public List<FlooRegistryEntry> getAllPublicEntries() {
        return entries.values().stream()
                .filter(FlooRegistryEntry::isPublic)
                .collect(Collectors.toList());
    }

    public void setEnabled(@NonNull String address, boolean enabled) {
        String key = address.strip().toLowerCase(Locale.ROOT);
        FlooRegistryEntry entry = entries.get(key);
        if (entry != null) {
            entries.put(key, entry.withEnabled(enabled));
            setDirty();
        }
    }

    public void logTravel(@NonNull Player player, @NonNull String fromAddress, @NonNull String toAddress) {
        String message = String.format("[FlooNetwork] %s travelled from \"%s\" to \"%s\" at %s",
                player.getName().getString(), fromAddress, toAddress, Instant.now());
        LOGGER.info(message);
        travelLog.addLast(message);
        while (travelLog.size() > MAX_LOG_ENTRIES) {
            travelLog.removeFirst();
        }
        setDirty();
    }

    @NonNull
    public List<String> getRecentLog(int count) {
        List<String> all = new ArrayList<>(travelLog);
        int start = Math.max(0, all.size() - count);
        return all.subList(start, all.size());
    }
}
