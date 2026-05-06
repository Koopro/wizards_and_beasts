package at.koopro.wizardsandbeasts.pocket;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.UUIDUtil;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class PocketSavedData extends SavedData {
    private static final Codec<Map<UUID, PocketRecord>> RECORD_MAP_CODEC = Codec.unboundedMap(
            UUIDUtil.CODEC,
            PocketRecord.CODEC);

    public static final SavedDataType<PocketSavedData> TYPE = new SavedDataType<>(
            WizardsAndBeastsMod.MODID + "_pocket_data",
            PocketSavedData::new,
            RecordCodecBuilder.create(instance -> instance.group(
                    RECORD_MAP_CODEC.optionalFieldOf("records", Map.of()).forGetter(data -> data.records),
                    Codec.unboundedMap(UUIDUtil.CODEC, UUIDUtil.CODEC).optionalFieldOf("caseBindings", Map.of())
                            .forGetter(data -> data.caseBindings)
            ).apply(instance, PocketSavedData::new)));

    private final Map<UUID, PocketRecord> records;
    private final Map<UUID, UUID> caseBindings;

    public PocketSavedData() {
        this(Map.of(), Map.of());
    }

    private PocketSavedData(Map<UUID, PocketRecord> records, Map<UUID, UUID> caseBindings) {
        this.records = new HashMap<>(records);
        this.caseBindings = new HashMap<>(caseBindings);
    }

    public static PocketSavedData get(ServerLevel level) {
        return level.getServer().overworld().getDataStorage().computeIfAbsent(TYPE);
    }

    public Optional<PocketRecord> getPocket(UUID pocketId) {
        return Optional.ofNullable(records.get(pocketId));
    }

    public Optional<PocketRecord> getCasePocket(UUID caseId) {
        UUID pocketId = caseBindings.get(caseId);
        if (pocketId == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(records.get(pocketId));
    }

    public void putRecord(PocketRecord record) {
        records.put(record.pocketId(), record);
        setDirty();
    }

    public void updateRecord(UUID pocketId, java.util.function.UnaryOperator<PocketRecord> updater) {
        PocketRecord current = records.get(pocketId);
        if (current == null) {
            return;
        }
        records.put(pocketId, updater.apply(current));
        setDirty();
    }

    public void bindCase(UUID caseId, UUID pocketId) {
        caseBindings.put(caseId, pocketId);
        setDirty();
    }

    public void setAccessMode(UUID pocketId, PocketAccessMode accessMode) {
        PocketRecord record = records.get(pocketId);
        if (record == null) {
            return;
        }
        records.put(pocketId, record.withAccessMode(accessMode));
        setDirty();
    }

    public void addMember(UUID pocketId, UUID memberId) {
        PocketRecord record = records.get(pocketId);
        if (record == null) {
            return;
        }
        records.put(pocketId, record.withMember(memberId));
        setDirty();
    }
}
