package at.koopro.wizardsandbeasts.wand.registry;

import at.koopro.wizardsandbeasts.spell.core.SpellCategory;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.Map;

/**
 * What a wand component contributes to a cast, in the vocabulary the cast path actually speaks.
 *
 * <p>Distinct from {@link WandWoodDefinition#spellModifiers()}, which is keyed by magical school
 * ({@code healing}, {@code divination}, …). Those schools have no counterpart in
 * {@link SpellCategory} and no consumer; mapping them is a balance decision that has not been made.
 * This record carries only what {@code WandStats} can already express, so a definition can describe
 * its cast contribution without anyone having to invent a number.
 *
 * <p>Every field is optional and defaults to the identity, so a definition that says nothing
 * contributes nothing — neutral, not zero.
 */
public record WandCastModifiers(
        float damage,
        float cooldown,
        float range,
        float fizzle,
        Map<SpellCategory, Float> categoryDamageBonus) {

    public static final WandCastModifiers NEUTRAL =
            new WandCastModifiers(1.0f, 1.0f, 1.0f, 0.0f, Map.of());

    public static final Codec<WandCastModifiers> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.FLOAT.optionalFieldOf("damage", 1.0f).forGetter(WandCastModifiers::damage),
            Codec.FLOAT.optionalFieldOf("cooldown", 1.0f).forGetter(WandCastModifiers::cooldown),
            Codec.FLOAT.optionalFieldOf("range", 1.0f).forGetter(WandCastModifiers::range),
            Codec.FLOAT.optionalFieldOf("fizzle", 0.0f).forGetter(WandCastModifiers::fizzle),
            Codec.unboundedMap(SpellCategory.CODEC, Codec.FLOAT)
                    .optionalFieldOf("category_damage_bonus", Map.of())
                    .forGetter(WandCastModifiers::categoryDamageBonus)
    ).apply(instance, WandCastModifiers::new));

    public WandCastModifiers {
        categoryDamageBonus = Map.copyOf(categoryDamageBonus);
    }

    /** True when this contributes nothing, so callers can skip the builder work entirely. */
    public boolean isNeutral() {
        return damage == 1.0f && cooldown == 1.0f && range == 1.0f && fizzle == 0.0f
                && categoryDamageBonus.isEmpty();
    }
}
