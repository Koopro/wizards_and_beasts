package at.koopro.wizardsandbeasts.map;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.UUIDUtil;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Where every Marauder's Map's {@link MapAtlas} lives on disk, keyed by the map's own id.
 *
 * <p>The alternative — keeping the atlas in the item's {@code CUSTOM_DATA} — was rejected on
 * bandwidth: an {@code ItemStack}'s components are re-sent to the client on every inventory change,
 * so a map of a well-explored world would be re-serialised and shipped whenever the holder picked
 * up a cobblestone. Here the item carries sixteen bytes of id and the atlas is fetched once, when
 * the map is opened.
 *
 * <p>World-scoped, on the overworld's storage, exactly as {@code FlooNetworkManager} is: a map
 * carried into the Nether is the same map, and its Overworld pages must not be in another file.
 */
public final class MaraudersMapAtlasStore extends SavedData {

    private record StoredEntry(UUID id, MapAtlas atlas) {
        static final Codec<StoredEntry> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                UUIDUtil.CODEC.fieldOf("id").forGetter(StoredEntry::id),
                MapAtlas.CODEC.fieldOf("atlas").forGetter(StoredEntry::atlas)
        ).apply(instance, StoredEntry::new));
    }

    public static final SavedDataType<MaraudersMapAtlasStore> TYPE = new SavedDataType<>(
            WizardsAndBeastsMod.MODID + "_marauders_map_atlas",
            MaraudersMapAtlasStore::new,
            RecordCodecBuilder.create(instance -> instance.group(
                    StoredEntry.CODEC.listOf().optionalFieldOf("maps", List.of())
                            .forGetter(MaraudersMapAtlasStore::store)
            ).apply(instance, MaraudersMapAtlasStore::fromStored)));

    private final Map<UUID, MapAtlas> atlases;

    public MaraudersMapAtlasStore() {
        this(new LinkedHashMap<>());
    }

    private MaraudersMapAtlasStore(Map<UUID, MapAtlas> atlases) {
        this.atlases = atlases;
    }

    private static MaraudersMapAtlasStore fromStored(List<StoredEntry> entries) {
        Map<UUID, MapAtlas> map = new LinkedHashMap<>();
        for (StoredEntry entry : entries) {
            map.put(entry.id(), entry.atlas());
        }
        return new MaraudersMapAtlasStore(map);
    }

    private List<StoredEntry> store() {
        List<StoredEntry> out = new ArrayList<>(atlases.size());
        for (Map.Entry<UUID, MapAtlas> entry : atlases.entrySet()) {
            out.add(new StoredEntry(entry.getKey(), entry.getValue()));
        }
        return out;
    }

    public static MaraudersMapAtlasStore get(@NonNull ServerLevel level) {
        return level.getServer().overworld().getDataStorage().computeIfAbsent(TYPE);
    }

    /** The atlas for a map, created empty the first time that map is opened. */
    public MapAtlas atlas(@NonNull UUID mapId) {
        return atlases.computeIfAbsent(mapId, k -> {
            setDirty();
            return new MapAtlas();
        });
    }

    /** The atlas for a map if it has one, without creating one. */
    public @Nullable MapAtlas peek(@NonNull UUID mapId) {
        return atlases.get(mapId);
    }

    /**
     * Marks the store for saving. Public because the surveyor and the discovery passes mutate the
     * {@link MapAtlas} they were handed rather than going back through this class for every tile —
     * a per-tile round trip through a saved-data lookup is exactly the sort of thing that turns a
     * chunk survey into a profiler entry.
     */
    public void markChanged() {
        setDirty();
    }

    /** Forgets a dimension across every map, for when a datapack dimension is removed. */
    public void forgetDimension(Identifier dimension) {
        boolean changed = false;
        for (MapAtlas atlas : atlases.values()) {
            changed |= atlas.forgetDimension(dimension);
        }
        if (changed) {
            setDirty();
        }
    }
}
