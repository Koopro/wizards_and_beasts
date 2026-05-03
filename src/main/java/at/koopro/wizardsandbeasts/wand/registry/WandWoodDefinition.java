package at.koopro.wizardsandbeasts.wand.registry;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;

import java.util.List;
import java.util.Map;

public record WandWoodDefinition(
        Component displayName,
        List<String> affinityTags,
        Map<String, Float> spellModifiers,
        List<String> personalityAffinity,
        String rarity,
        float refuseThreshold) {
    public static final Codec<WandWoodDefinition> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            ComponentSerialization.CODEC.fieldOf("display_name").forGetter(WandWoodDefinition::displayName),
            Codec.STRING.listOf().fieldOf("affinity_tags").forGetter(WandWoodDefinition::affinityTags),
            Codec.unboundedMap(Codec.STRING, Codec.FLOAT).fieldOf("spell_modifiers").forGetter(WandWoodDefinition::spellModifiers),
            Codec.STRING.listOf().fieldOf("personality_affinity").forGetter(WandWoodDefinition::personalityAffinity),
            Codec.STRING.fieldOf("rarity").forGetter(WandWoodDefinition::rarity),
            Codec.FLOAT.optionalFieldOf("refuse_threshold", 0.2f).forGetter(WandWoodDefinition::refuseThreshold)
    ).apply(instance, WandWoodDefinition::new));
}
