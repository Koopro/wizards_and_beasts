package at.koopro.wizardsandbeasts.wand.registry;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;

public record BenchEnhancerDefinition(
        Identifier blockId,
        float enhancementValue,
        String tierLabel) {
    public static final Codec<BenchEnhancerDefinition> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Identifier.CODEC.fieldOf("block_id").forGetter(BenchEnhancerDefinition::blockId),
            Codec.FLOAT.fieldOf("enhancement_value").forGetter(BenchEnhancerDefinition::enhancementValue),
            Codec.STRING.fieldOf("tier_label").forGetter(BenchEnhancerDefinition::tierLabel)
    ).apply(instance, BenchEnhancerDefinition::new));
}
