package at.koopro.wizardsandbeasts.spell.cast;

import at.koopro.wizardsandbeasts.spell.core.*;

import at.koopro.wizardsandbeasts.spell.def.SpellDefinition;
import at.koopro.wizardsandbeasts.spell.gamp.GampDomain;
import at.koopro.wizardsandbeasts.spell.proficiency.SpellScalingProfile;
import at.koopro.wizardsandbeasts.wand.cast.WandStats;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import org.jspecify.annotations.Nullable;
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
                SpellScalingProfile.DEFAULT,
                new ModifierStack(),
                new ArrayList<>());
    }

    public CastContext withGampPenalty(GampDomain domain) {
        return new CastContext(caster, wandStack, spell, definition, wandStats, proficiency, domain, scalingProfile, modifiers, rejections);
    }

    public CastContext withScalingProfile(SpellScalingProfile value) {
        return new CastContext(caster, wandStack, spell, definition, wandStats, proficiency, activePenalty, value, modifiers, rejections);
    }
}
