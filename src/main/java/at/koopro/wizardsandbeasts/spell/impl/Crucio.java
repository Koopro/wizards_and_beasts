package at.koopro.wizardsandbeasts.spell.impl;

import at.koopro.wizardsandbeasts.module.Module;
import at.koopro.wizardsandbeasts.module.ModuleManager;
import at.koopro.wizardsandbeasts.registry.ModSounds;
import at.koopro.wizardsandbeasts.spell.core.*;
import at.koopro.wizardsandbeasts.spell.cast.*;
import at.koopro.wizardsandbeasts.spell.lib.*;
import at.koopro.wizardsandbeasts.spell.beam.*;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.ItemStack;

public class Crucio extends Spell {

    public Crucio() {
        super("crucio", "Crucio", SpellCategory.DARK_ARTS, 220, 2.0f, 0xFFFF0044);
    }

    @Override
    public void executeCast(CastContext ctx, ServerLevel level) {
        ServerPlayer caster = ctx.caster();
        if (!ModuleManager.isEnabled(Module.DARK_ARTS)) {
            level.playSound(null, caster.blockPosition(), ModSounds.SPELL_FIZZLE.get(), SoundSource.PLAYERS, 0.5f, 1.0f);
            caster.displayClientMessage(Component.literal("\u00A78That power is sealed away."), true);
            return;
        }
        super.executeCast(ctx, level);
    }

    @Override
    protected SpellProperties buildProperties() {
        // Cruciatus inflicts extreme pain and incapacitation — not necrotic decay.
        // Modelled as weakness + disorientation + slowness rather than the lore-wrong WITHER.
        return SpellProperties.beamChannel(50.0f)
                .targetEffect(() -> new MobEffectInstance(MobEffects.WEAKNESS, 40, 1, false, true, true))
                .targetEffect(() -> new MobEffectInstance(MobEffects.NAUSEA, 40, 0, false, true, true))
                .targetEffect(() -> new MobEffectInstance(MobEffects.SLOWNESS, 40, 1, false, true, true))
                .sound(SoundEvents.BLAZE_SHOOT, 1.0f, 0.8f)
                .build();
    }

    @Override
    protected SpellRequirement buildRequirement() {
        return SpellRequirement.proficiency("incendio", Proficiency.MASTERED);
    }
}
