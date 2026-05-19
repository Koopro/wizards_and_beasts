package at.koopro.wizardsandbeasts.trunk;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import java.util.*;

public final class TrunkRegistryData extends SavedData {

    private static final Codec<UUID> UUID_KEY = Codec.STRING.xmap(UUID::fromString, UUID::toString);

    private static final Codec<Map<UUID, TrunkRecord>> RECORD_MAP_CODEC =
            Codec.unboundedMap(UUID_KEY, TrunkRecord.CODEC);

    public static final SavedDataType<TrunkRegistryData> TYPE = new SavedDataType<>(
            WizardsAndBeastsMod.MODID + "_trunk_registry",
            TrunkRegistryData::new,
            RecordCodecBuilder.create(instance -> instance.group(
                    RECORD_MAP_CODEC.optionalFieldOf("records", Map.of()).forGetter(d -> d.records),
                    Codec.unboundedMap(UUID_KEY, UUID_KEY).optionalFieldOf("caseBindings", Map.of()).forGetter(d -> d.caseBindings),
                    UUIDUtil.CODEC.listOf().optionalFieldOf("initializedPockets", List.of()).forGetter(d -> List.copyOf(d.initializedPockets)),
                    Codec.unboundedMap(UUID_KEY, BlockPos.CODEC).optionalFieldOf("returnPositions", Map.of()).forGetter(d -> d.playerReturnPositions),
                    Codec.unboundedMap(UUID_KEY, Codec.STRING).optionalFieldOf("returnDimensions", Map.of()).forGetter(d -> d.playerReturnDimensions),
                    UUIDUtil.CODEC.listOf().optionalFieldOf("impoundedPockets", List.of()).forGetter(d -> List.copyOf(d.impoundedPockets))
            ).apply(instance, TrunkRegistryData::new)));

    private final Map<UUID, TrunkRecord> records;
    private final Map<UUID, UUID> caseBindings;
    private final Set<UUID> initializedPockets;
    private final Map<UUID, BlockPos> playerReturnPositions;
    private final Map<UUID, String> playerReturnDimensions;
    private final Set<UUID> impoundedPockets;

    public TrunkRegistryData() {
        this(Map.of(), Map.of(), List.of(), Map.of(), Map.of(), List.of());
    }

    private TrunkRegistryData(Map<UUID, TrunkRecord> records,
                               Map<UUID, UUID> caseBindings,
                               List<UUID> initializedPockets,
                               Map<UUID, BlockPos> playerReturnPositions,
                               Map<UUID, String> playerReturnDimensions,
                               List<UUID> impoundedPockets) {
        this.records = new HashMap<>(records);
        this.caseBindings = new HashMap<>(caseBindings);
        this.initializedPockets = new HashSet<>(initializedPockets);
        this.playerReturnPositions = new HashMap<>(playerReturnPositions);
        this.playerReturnDimensions = new HashMap<>(playerReturnDimensions);
        this.impoundedPockets = new HashSet<>(impoundedPockets);
    }

    public static TrunkRegistryData get(ServerLevel level) {
        return level.getServer().overworld().getDataStorage().computeIfAbsent(TYPE);
    }

    // --- Trunk record access ---

    public Optional<TrunkRecord> getPocket(UUID pocketId) {
        return Optional.ofNullable(records.get(pocketId));
    }

    public Optional<TrunkRecord> getCasePocket(UUID caseId) {
        UUID pocketId = caseBindings.get(caseId);
        if (pocketId == null) return Optional.empty();
        return Optional.ofNullable(records.get(pocketId));
    }

    public Optional<TrunkRecord> getPocketAtPos(BlockPos pos) {
        int gridX = Math.floorDiv(pos.getX(), ExtensionCharmService.GRID_SPACING);
        int gridZ = Math.floorDiv(pos.getZ(), ExtensionCharmService.GRID_SPACING);
        for (TrunkRecord r : records.values()) {
            if (Math.floorDiv(r.spawnPos().getX(), ExtensionCharmService.GRID_SPACING) == gridX
                    && Math.floorDiv(r.spawnPos().getZ(), ExtensionCharmService.GRID_SPACING) == gridZ) {
                return Optional.of(r);
            }
        }
        return Optional.empty();
    }

    public void putRecord(TrunkRecord record) {
        records.put(record.pocketId(), record);
        setDirty();
    }

    public void updateRecord(UUID pocketId, java.util.function.UnaryOperator<TrunkRecord> updater) {
        TrunkRecord current = records.get(pocketId);
        if (current == null) return;
        records.put(pocketId, updater.apply(current));
        setDirty();
    }

    public void bindCase(UUID caseId, UUID pocketId) {
        caseBindings.put(caseId, pocketId);
        setDirty();
    }

    public void setAccessMode(UUID pocketId, TrunkAccessMode accessMode) {
        TrunkRecord record = records.get(pocketId);
        if (record == null) return;
        records.put(pocketId, record.withAccessMode(accessMode));
        setDirty();
    }

    public void addMember(UUID pocketId, UUID memberId) {
        TrunkRecord record = records.get(pocketId);
        if (record == null) return;
        records.put(pocketId, record.withMember(memberId));
        setDirty();
    }

    // --- Shell initialization tracking ---

    public boolean isInitialized(UUID pocketId) {
        return initializedPockets.contains(pocketId);
    }

    public void markInitialized(UUID pocketId) {
        initializedPockets.add(pocketId);
        setDirty();
    }

    public void markUninitialized(UUID pocketId) {
        initializedPockets.remove(pocketId);
        setDirty();
    }

    public List<TrunkRecord> getAllPockets() {
        return List.copyOf(records.values());
    }

    // --- Return position tracking ---

    public void saveReturnPosition(UUID playerId, BlockPos pos, ResourceKey<Level> dim) {
        playerReturnPositions.put(playerId, pos);
        playerReturnDimensions.put(playerId, dim.identifier().toString());
        setDirty();
    }

    public Optional<BlockPos> getReturnPosition(UUID playerId) {
        return Optional.ofNullable(playerReturnPositions.get(playerId));
    }

    public Optional<String> getReturnDimension(UUID playerId) {
        return Optional.ofNullable(playerReturnDimensions.get(playerId));
    }

    public void clearReturnData(UUID playerId) {
        playerReturnPositions.remove(playerId);
        playerReturnDimensions.remove(playerId);
        setDirty();
    }

    // --- Impound tracking ---

    public boolean isImpounded(UUID pocketId) {
        return impoundedPockets.contains(pocketId);
    }

    public void setImpounded(UUID pocketId, boolean impounded) {
        if (impounded) {
            impoundedPockets.add(pocketId);
        } else {
            impoundedPockets.remove(pocketId);
        }
        setDirty();
    }
}
