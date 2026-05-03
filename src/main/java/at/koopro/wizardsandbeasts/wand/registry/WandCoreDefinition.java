package at.koopro.wizardsandbeasts.wand.registry;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;

public record WandCoreDefinition(
        Component displayName,
        String sourceKey,
        float rawPower,
        float consistency,
        float loyalty,
        float darkAffinity,
        float initiative,
        float allegianceTransferResistance) {
    public static final Codec<WandCoreDefinition> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            ComponentSerialization.CODEC.fieldOf("display_name").forGetter(WandCoreDefinition::displayName),
            Codec.STRING.fieldOf("source_key").forGetter(WandCoreDefinition::sourceKey),
            Codec.FLOAT.fieldOf("raw_power").forGetter(WandCoreDefinition::rawPower),
            Codec.FLOAT.fieldOf("consistency").forGetter(WandCoreDefinition::consistency),
            Codec.FLOAT.fieldOf("loyalty").forGetter(WandCoreDefinition::loyalty),
            Codec.FLOAT.fieldOf("dark_affinity").forGetter(WandCoreDefinition::darkAffinity),
            Codec.FLOAT.fieldOf("initiative").forGetter(WandCoreDefinition::initiative),
            Codec.FLOAT.fieldOf("allegiance_transfer_resistance").forGetter(WandCoreDefinition::allegianceTransferResistance)
    ).apply(instance, WandCoreDefinition::new));
}
