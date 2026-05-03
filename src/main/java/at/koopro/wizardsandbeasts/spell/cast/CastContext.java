package at.koopro.wizardsandbeasts.spell.cast;

import at.koopro.wizardsandbeasts.spell.Proficiency;
import at.koopro.wizardsandbeasts.spell.Spell;
import at.koopro.wizardsandbeasts.spell.def.SpellDefinition;
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
                new ModifierStack(),
                new ArrayList<>());
    }

    public CastContext withAllegiance(@Nullable WandAllegiance value) {
        return new CastContext(caster, wandStack, spell, definition, wandStats, proficiency, value, compatibility, modifiers, rejections);
    }

    public CastContext withCompatibility(@Nullable Compatibility.Score value) {
        return new CastContext(caster, wandStack, spell, definition, wandStats, proficiency, allegiance, value, modifiers, rejections);
    }
}
