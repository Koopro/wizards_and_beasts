package at.koopro.wizardsandbeasts.floo;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NonNull;

import java.util.Optional;
import java.util.UUID;

/**
 * One hearth on the network.
 *
 * <h2>Ownership</h2>
 * <p>{@code owner} is the player who registered the address, and it is {@link Optional} because it
 * has to be: every entry written before ownership existed has none, and inventing one would be worse
 * than admitting it. An unowned entry behaves the way the whole network did before — anyone may
 * unregister it, anyone may change its visibility — which keeps old worlds working instead of
 * quietly locking their hearths against everybody.
 *
 * <p>Stored rather than derived. A hearth's owner is not "whoever can reach the block": that would
 * hand a home over to the first person to break in, which is precisely the thing a private address
 * exists to prevent.
 */
public record FlooRegistryEntry(
        @NonNull String networkAddress,
        @NonNull Identifier dimension,
        @NonNull BlockPos blockPos,
        boolean isEnabled,
        boolean isPublic,
        @NonNull Optional<UUID> owner
) {
    public static final Codec<FlooRegistryEntry> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.fieldOf("address").forGetter(FlooRegistryEntry::networkAddress),
            Identifier.CODEC.fieldOf("dimension").forGetter(FlooRegistryEntry::dimension),
            BlockPos.CODEC.fieldOf("pos").forGetter(FlooRegistryEntry::blockPos),
            Codec.BOOL.optionalFieldOf("enabled", true).forGetter(FlooRegistryEntry::isEnabled),
            Codec.BOOL.optionalFieldOf("public", true).forGetter(FlooRegistryEntry::isPublic),
            // UUIDUtil.CODEC is the int-array form, and that is fine here precisely because nothing
            // outside this record ever writes the field: it is round-tripped by the same codec that
            // produced it. Hand-written UUID strings in a datapack would need LENIENT_CODEC instead.
            UUIDUtil.CODEC.optionalFieldOf("owner").forGetter(FlooRegistryEntry::owner)
    ).apply(instance, FlooRegistryEntry::new));

    /** Convenience for the common case of an entry nobody claimed. */
    public static FlooRegistryEntry unowned(@NonNull String address, @NonNull Identifier dimension,
                                            @NonNull BlockPos pos, boolean isPublic) {
        return new FlooRegistryEntry(address, dimension, pos, true, isPublic, Optional.empty());
    }

    public FlooRegistryEntry withEnabled(boolean enabled) {
        return new FlooRegistryEntry(networkAddress, dimension, blockPos, enabled, isPublic, owner);
    }

    public FlooRegistryEntry withPublic(boolean isPublic) {
        return new FlooRegistryEntry(networkAddress, dimension, blockPos, isEnabled, isPublic, owner);
    }

    /**
     * Whether {@code playerId} is this hearth's registered owner.
     *
     * <p>False for an unowned entry, which is why callers pair it with an explicit "or nobody owns
     * this" clause rather than treating an absent owner as a match. Those are different questions:
     * "are you the owner" and "may you act on this" only coincide once you have decided what an
     * ownerless hearth means, and that decision belongs at the call site.
     */
    public boolean isOwnedBy(@NonNull UUID playerId) {
        return owner.isPresent() && owner.get().equals(playerId);
    }
}
