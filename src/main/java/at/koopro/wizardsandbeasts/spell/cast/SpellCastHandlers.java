package at.koopro.wizardsandbeasts.spell.cast;

import at.koopro.wizardsandbeasts.spell.core.*;

import at.koopro.wizardsandbeasts.spell.proficiency.SpellScalingProfile;
import at.koopro.wizardsandbeasts.wand.cast.WandStats;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

final class SpellCastHandlers {

    private SpellCastHandlers() {}

    static boolean handleCone(ServerLevel level, ServerPlayer caster,
                              Spell spell, SpellProperties props,
                              float damageMultiplier, WandStats wand, SpellScalingProfile scalingProfile) {
        return SpellCastConeHandler.handle(level, caster, spell, props, damageMultiplier, wand, scalingProfile);
    }

    static boolean handleTargeted(ServerLevel level, ServerPlayer caster,
                                  Spell spell, SpellProperties props,
                                  float damageMultiplier, WandStats wand, SpellScalingProfile scalingProfile) {
        return SpellCastTargetedHandler.handle(level, caster, spell, props, damageMultiplier, wand, scalingProfile);
    }

    static boolean handleEpiskeySelf(ServerPlayer caster, Spell spell) {
        return SpellCastUtilityHandler.handleEpiskeySelf(caster);
    }

    static boolean handleFrigoraSelf(ServerLevel level, ServerPlayer caster, Spell spell) {
        return SpellCastUtilityHandler.handleFrigoraSelf(level, caster, spell);
    }

    static boolean handleRepair(ServerPlayer caster, ItemStack wandStack, int repairAmount) {
        return SpellCastUtilityHandler.handleRepair(caster, wandStack);
    }

    static boolean handleReparoSelf(ServerLevel level, ServerPlayer caster, Spell spell) {
        return SpellCastUtilityHandler.handleReparoSelf(level, caster, spell);
    }

    static boolean handlePocketIngress(ServerPlayer caster) {
        return SpellCastUtilityHandler.handlePocketIngress(caster);
    }

    static boolean handlePocketEgress(ServerPlayer caster) {
        return SpellCastUtilityHandler.handlePocketEgress(caster);
    }

}
