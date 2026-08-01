package at.koopro.wizardsandbeasts.wand.registry;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;

import java.util.List;
import java.util.Map;

/**
 * @param spellModifiers keyed by magical school ({@code healing}, {@code divination}, …). Read by
 *                       nothing: those schools have no {@link at.koopro.wizardsandbeasts.spell.core.SpellCategory}
 *                       counterpart, and choosing the mapping is a balance decision that has not
 *                       been made. Kept as authored so it is ready when that lands.
 * @param castModifiers  what the wood actually contributes to a cast. Absent means neutral.
 */
public record WandWoodDefinition(
        Component displayName,
        List<String> affinityTags,
        Map<String, Float> spellModifiers,
        List<String> personalityAffinity,
        String rarity,
        float refuseThreshold,
        WandCastModifiers castModifiers) {
    public static final Codec<WandWoodDefinition> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            ComponentSerialization.CODEC.fieldOf("display_name").forGetter(WandWoodDefinition::displayName),
            Codec.STRING.listOf().fieldOf("affinity_tags").forGetter(WandWoodDefinition::affinityTags),
            Codec.unboundedMap(Codec.STRING, Codec.FLOAT).fieldOf("spell_modifiers").forGetter(WandWoodDefinition::spellModifiers),
            Codec.STRING.listOf().fieldOf("personality_affinity").forGetter(WandWoodDefinition::personalityAffinity),
            Codec.STRING.fieldOf("rarity").forGetter(WandWoodDefinition::rarity),
            Codec.FLOAT.optionalFieldOf("refuse_threshold", 0.2f).forGetter(WandWoodDefinition::refuseThreshold),
            WandCastModifiers.CODEC.optionalFieldOf("cast_modifiers", WandCastModifiers.NEUTRAL)
                    .forGetter(WandWoodDefinition::castModifiers)
    ).apply(instance, WandWoodDefinition::new));
}
