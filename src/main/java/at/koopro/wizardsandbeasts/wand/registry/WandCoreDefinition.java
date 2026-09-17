package at.koopro.wizardsandbeasts.wand.registry;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;

/**
 * @param castModifiers what the core contributes to a cast. Absent means neutral. Same shared
 *                      {@link WandCastModifiers} record {@link WandWoodDefinition} uses; cores and woods
 *                      contribute in exactly the same vocabulary, so they share exactly one type.
 * @param temperament   how the core behaves toward its wielder — see {@link WandTemperament}. Absent means
 *                      neutral. {@code consistency}, {@code loyalty}, {@code darkAffinity} and
 *                      {@code initiative} predate it and are read by nothing; temperament is where those
 *                      traits act.
 */
public record WandCoreDefinition(
        Component displayName,
        String sourceKey,
        float rawPower,
        float consistency,
        float loyalty,
        float darkAffinity,
        float initiative,
        float allegianceTransferResistance,
        WandCastModifiers castModifiers,
        WandTemperament temperament) {
    public static final Codec<WandCoreDefinition> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            ComponentSerialization.CODEC.fieldOf("display_name").forGetter(WandCoreDefinition::displayName),
            Codec.STRING.fieldOf("source_key").forGetter(WandCoreDefinition::sourceKey),
            Codec.FLOAT.fieldOf("raw_power").forGetter(WandCoreDefinition::rawPower),
            Codec.FLOAT.fieldOf("consistency").forGetter(WandCoreDefinition::consistency),
            Codec.FLOAT.fieldOf("loyalty").forGetter(WandCoreDefinition::loyalty),
            Codec.FLOAT.fieldOf("dark_affinity").forGetter(WandCoreDefinition::darkAffinity),
            Codec.FLOAT.fieldOf("initiative").forGetter(WandCoreDefinition::initiative),
            Codec.FLOAT.fieldOf("allegiance_transfer_resistance").forGetter(WandCoreDefinition::allegianceTransferResistance),
            WandCastModifiers.CODEC.optionalFieldOf("cast_modifiers", WandCastModifiers.NEUTRAL)
                    .forGetter(WandCoreDefinition::castModifiers),
            WandTemperament.CODEC.optionalFieldOf("temperament", WandTemperament.NEUTRAL)
                    .forGetter(WandCoreDefinition::temperament)
    ).apply(instance, WandCoreDefinition::new));
}
