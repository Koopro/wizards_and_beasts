package at.koopro.wizardsandbeasts.azkaban.data;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.Optional;

public final class AzkabanWorldData extends SavedData {

    public static final SavedDataType<AzkabanWorldData> TYPE = new SavedDataType<>(
            WizardsAndBeastsMod.MODID + "_azkaban_world",
            AzkabanWorldData::new,
            RecordCodecBuilder.create(instance -> instance.group(
                    BlockPos.CODEC.optionalFieldOf("fortress_center")
                            .forGetter(d -> Optional.ofNullable(d.fortressCenter)),
                    Codec.BOOL.optionalFieldOf("generated", false)
                            .forGetter(d -> d.generated)
            ).apply(instance, (centerOpt, gen) ->
                    new AzkabanWorldData(centerOpt.orElse(null), gen))));

    private @Nullable BlockPos fortressCenter;
    private boolean generated;

    public AzkabanWorldData() {}

    private AzkabanWorldData(@Nullable BlockPos center, boolean generated) {
        this.fortressCenter = center;
        this.generated = generated;
    }

    public static AzkabanWorldData get(ServerLevel level) {
        return level.getServer().overworld().getDataStorage().computeIfAbsent(TYPE);
    }

    /**
     * Cached fortress center, or {@code null} if the structure has not generated
     * yet. With random-spread placement there is no seed-derivable location — use
     * {@code /locate} (a structure search) to find an instance.
     */
    public @Nullable BlockPos getCenter() {
        return fortressCenter;
    }

    public void setFortressCenter(@NonNull BlockPos center) {
        this.fortressCenter = center;
        this.generated = true;
        setDirty();
    }

    public boolean isGenerated() {
        return generated;
    }
}
