package at.koopro.wizardsandbeasts.wand.elder;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.UUIDUtil;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import javax.annotation.Nullable;
import java.util.Optional;
import java.util.UUID;

public final class ElderWandSavedData extends SavedData {

    public static final SavedDataType<ElderWandSavedData> TYPE = new SavedDataType<>(
            WizardsAndBeastsMod.MODID + "_elder_wand",
            ElderWandSavedData::new,
            RecordCodecBuilder.create(instance -> instance.group(
                    UUIDUtil.CODEC.optionalFieldOf("instanceId")
                            .forGetter(d -> Optional.ofNullable(d.instanceId)),
                    UUIDUtil.CODEC.optionalFieldOf("masterUuid")
                            .forGetter(d -> Optional.ofNullable(d.masterUuid))
            ).apply(instance, (inst, master) ->
                    new ElderWandSavedData(inst.orElse(null), master.orElse(null)))));

    @Nullable private UUID instanceId;
    @Nullable private UUID masterUuid;

    public ElderWandSavedData() {
        this(null, null);
    }

    private ElderWandSavedData(@Nullable UUID instanceId, @Nullable UUID masterUuid) {
        this.instanceId = instanceId;
        this.masterUuid = masterUuid;
    }

    public static ElderWandSavedData get(ServerLevel level) {
        return level.getServer().overworld().getDataStorage().computeIfAbsent(TYPE);
    }

    public boolean isRegistered() {
        return instanceId != null;
    }

    public @Nullable UUID getInstanceId() {
        return instanceId;
    }

    public @Nullable UUID getMaster() {
        return masterUuid;
    }

    public void register(UUID id) {
        this.instanceId = id;
        setDirty();
    }

    public void setMaster(@Nullable UUID master) {
        this.masterUuid = master;
        setDirty();
    }

    public void reset() {
        this.instanceId = null;
        this.masterUuid = null;
        setDirty();
    }
}
