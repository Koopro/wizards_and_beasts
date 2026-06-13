package at.koopro.wizardsandbeasts.spell.effect;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * One authored entry in a spell's top-level {@code effects} list: a {@link SpellEffectComponent}
 * plus an optional {@code cadence} tag (default {@link EffectCadence#TICK}). The cadence field
 * rides beside the component's own fields in the same flat JSON object, so every pre-cadence
 * component JSON parses unchanged.
 *
 * <p>Cadence is honored only by the BEAM_CHANNEL per-tick dispatch ({@code WandBeamChannelLogic});
 * all other cast types run the full list once, cadence-inert. Cadence is top-level only: nested
 * {@code aoe_apply} children stay plain {@link SpellEffectComponent}s and fire when their parent
 * fires.
 */
public record SpellEffectEntry(SpellEffectComponent component, EffectCadence cadence) {

    public static final Codec<SpellEffectEntry> CODEC = RecordCodecBuilder.create(inst -> inst.group(
            SpellEffectComponent.MAP_CODEC.forGetter(SpellEffectEntry::component),
            EffectCadence.CODEC.optionalFieldOf("cadence", EffectCadence.TICK).forGetter(SpellEffectEntry::cadence)
    ).apply(inst, SpellEffectEntry::new));

    /** Applies the wrapped component (the component self-gates on its required modules). */
    public void apply(SpellEffectContext ctx) {
        component.apply(ctx);
    }
}
