package at.koopro.wizardsandbeasts.entity.niffler;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import org.jspecify.annotations.Nullable;

import java.util.Optional;
import java.util.UUID;

/**
 * Player attachment tracking which Niffler UUID (if any) is being carried in the pocket.
 * Persisted across dimension travel; dropped on player death (copyOnDeath = false).
 */
public record CarriedNifflerAttachment(@Nullable UUID nifflerUUID) {

    public static final CarriedNifflerAttachment EMPTY = new CarriedNifflerAttachment(null);

    public static final Codec<CarriedNifflerAttachment> CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                    com.mojang.serialization.Codec.STRING
                            .optionalFieldOf("niffler_uuid")
                            .forGetter(a -> Optional.ofNullable(a.nifflerUUID).map(UUID::toString))
            ).apply(instance, opt -> new CarriedNifflerAttachment(opt.map(UUID::fromString).orElse(null)))
    );

    public boolean isCarrying() { return nifflerUUID != null; }
}
