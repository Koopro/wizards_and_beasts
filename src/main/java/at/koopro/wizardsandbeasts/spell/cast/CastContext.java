package at.koopro.wizardsandbeasts.spell.cast;

import at.koopro.wizardsandbeasts.spell.core.*;

import at.koopro.wizardsandbeasts.spell.def.SpellDefinition;
import at.koopro.wizardsandbeasts.spell.gamp.GampDomain;
import at.koopro.wizardsandbeasts.spell.proficiency.SpellScalingProfile;
import at.koopro.wizardsandbeasts.wand.cast.Compatibility;
import at.koopro.wizardsandbeasts.wand.cast.WandAllegiance;
import at.koopro.wizardsandbeasts.wand.cast.WandStats;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * Single cast-attempt envelope passed through the full cast pipeline.
 */
public record CastContext(
        ServerPlayer caster,
        ItemStack wandStack,
        Spell spell,
        @Nullable SpellDefinition definition,
        WandStats wandStats,
        Proficiency proficiency,
        @Nullable WandAllegiance allegiance,
        @Nullable Compatibility.Score compatibility,
        @Nullable GampDomain activePenalty,
        SpellScalingProfile scalingProfile,
        ModifierStack modifiers,
        List<RejectReason> rejections
) {
    public static CastContext create(ServerPlayer caster,
                                     ItemStack wandStack,
                                     Spell spell,
                                     @Nullable SpellDefinition definition,
                                     WandStats wandStats,
                                     Proficiency proficiency) {
        return new CastContext(
                caster,
                wandStack,
                spell,
                definition,
                wandStats,
                proficiency,
                null,
                null,
                null,
                SpellScalingProfile.DEFAULT,
                new ModifierStack(),
                new ArrayList<>());
    }

    public CastContext withAllegiance(@Nullable WandAllegiance value) {
        return new CastContext(caster, wandStack, spell, definition, wandStats, proficiency, value, compatibility, activePenalty, scalingProfile, modifiers, rejections);
    }

    public CastContext withCompatibility(@Nullable Compatibility.Score value) {
        return new CastContext(caster, wandStack, spell, definition, wandStats, proficiency, allegiance, value, activePenalty, scalingProfile, modifiers, rejections);
    }

    public CastContext withGampPenalty(GampDomain domain) {
        return new CastContext(caster, wandStack, spell, definition, wandStats, proficiency, allegiance, compatibility, domain, scalingProfile, modifiers, rejections);
    }

    public CastContext withScalingProfile(SpellScalingProfile value) {
        return new CastContext(caster, wandStack, spell, definition, wandStats, proficiency, allegiance, compatibility, activePenalty, value, modifiers, rejections);
    }
}
