package at.koopro.wizardsandbeasts.wand.registry;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;

/**
 * @param castModifiers what the core contributes to a cast. Absent means neutral. Same shared
 *                      {@link WandCastModifiers} record {@link WandWoodDefinition} uses; cores and woods
 *                      contribute in exactly the same vocabulary, so they share exactly one type.
 * @param consistency   how steady the core is; moves misfire around 0.7 ({@code WandStatsResolver.consistencyFizzle})
 * @param darkAffinity  how readily it lends itself to the Dark Arts: Dark Arts power around 0.5 and how fast Dark
 *                      casts corrupt the wand ({@code WandStatsResolver.darkArtsBonus}, {@code corruptionRate})
 * @param temperament   how the core behaves toward its wielder — see {@link WandTemperament}. Absent means
 *                      neutral. The old {@code loyalty}, {@code initiative} and
 *                      {@code allegiance_transfer_resistance} fields were read by nothing and duplicated what
 *                      temperament does (bond growth, extra wins, transfer bonus); they were removed on
 *                      2026-10-22, and a datapack that still carries them is read without error.
 * @param appearance    how the core shows through the wand — see {@link WandCoreAppearance}. Absent means a
 *                      muted light derived from the core's id. Looks only; nothing in a cast reads it.
 */
public record WandCoreDefinition(
        Component displayName,
        String sourceKey,
        float rawPower,
        float consistency,
        float darkAffinity,
        WandCastModifiers castModifiers,
        WandTemperament temperament,
        WandCoreAppearance appearance) {
    public static final Codec<WandCoreDefinition> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            ComponentSerialization.CODEC.fieldOf("display_name").forGetter(WandCoreDefinition::displayName),
            Codec.STRING.fieldOf("source_key").forGetter(WandCoreDefinition::sourceKey),
            Codec.FLOAT.fieldOf("raw_power").forGetter(WandCoreDefinition::rawPower),
            Codec.FLOAT.fieldOf("consistency").forGetter(WandCoreDefinition::consistency),
            Codec.FLOAT.fieldOf("dark_affinity").forGetter(WandCoreDefinition::darkAffinity),
            WandCastModifiers.CODEC.optionalFieldOf("cast_modifiers", WandCastModifiers.NEUTRAL)
                    .forGetter(WandCoreDefinition::castModifiers),
            WandTemperament.CODEC.optionalFieldOf("temperament", WandTemperament.NEUTRAL)
                    .forGetter(WandCoreDefinition::temperament),
            WandCoreAppearance.CODEC.optionalFieldOf("appearance", WandCoreAppearance.NONE)
                    .forGetter(WandCoreDefinition::appearance)
    ).apply(instance, WandCoreDefinition::new));
}
