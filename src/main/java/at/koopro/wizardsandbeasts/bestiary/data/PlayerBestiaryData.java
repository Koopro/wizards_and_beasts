package at.koopro.wizardsandbeasts.bestiary.data;

import at.koopro.wizardsandbeasts.bestiary.DiscoveryTier;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;

import java.util.HashMap;
import java.util.Map;

public record PlayerBestiaryData(Map<Identifier, DiscoveryTier> tiers) {
    public static final Codec<PlayerBestiaryData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.unboundedMap(Identifier.CODEC, DiscoveryTier.CODEC)
                    .optionalFieldOf("tiers", Map.of())
                    .forGetter(PlayerBestiaryData::tiers)
    ).apply(instance, m -> new PlayerBestiaryData(new HashMap<>(m))));
}
