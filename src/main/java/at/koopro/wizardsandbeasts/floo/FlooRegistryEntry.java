package at.koopro.wizardsandbeasts.floo;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NonNull;

public record FlooRegistryEntry(
        @NonNull String networkAddress,
        @NonNull Identifier dimension,
        @NonNull BlockPos blockPos,
        boolean isEnabled,
        boolean isPublic
) {
    public static final Codec<FlooRegistryEntry> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.fieldOf("address").forGetter(FlooRegistryEntry::networkAddress),
            Identifier.CODEC.fieldOf("dimension").forGetter(FlooRegistryEntry::dimension),
            BlockPos.CODEC.fieldOf("pos").forGetter(FlooRegistryEntry::blockPos),
            Codec.BOOL.optionalFieldOf("enabled", true).forGetter(FlooRegistryEntry::isEnabled),
            Codec.BOOL.optionalFieldOf("public", true).forGetter(FlooRegistryEntry::isPublic)
    ).apply(instance, FlooRegistryEntry::new));

    public FlooRegistryEntry withEnabled(boolean enabled) {
        return new FlooRegistryEntry(networkAddress, dimension, blockPos, enabled, isPublic);
    }
}
