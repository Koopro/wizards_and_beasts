package at.koopro.wizardsandbeasts.spell.impl;

import at.koopro.wizardsandbeasts.effect.ModEffects;
import at.koopro.wizardsandbeasts.module.Module;
import at.koopro.wizardsandbeasts.module.ModuleManager;
import at.koopro.wizardsandbeasts.spell.core.*;
import at.koopro.wizardsandbeasts.spell.cast.*;
import at.koopro.wizardsandbeasts.spell.lib.*;
import at.koopro.wizardsandbeasts.spell.beam.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.item.ItemStack;

public class Lumos extends Spell {

    public Lumos() {
        super("lumos", "Lumos", SpellCategory.UTILITY, 200, 0.0f, 0xFFFFFF88);
    }

    @Override
    protected SpellProperties buildProperties() {
        return SpellProperties.self()
                .selfEffect(() -> ModuleManager.isEnabled(Module.WANDS_AND_SPELLS)
                        ? new MobEffectInstance(ModEffects.LUMOS_FIELD, -1, 0, false, true, true)
                        : null)
                .sound(SoundEvents.EXPERIENCE_ORB_PICKUP, 0.5f, 1.5f)
                .build();
    }

    @Override
    protected SpellRequirement buildRequirement() {
        return SpellRequirement.none();
    }

    @Override
    public void execute(ServerLevel level, ServerPlayer caster, ItemStack wandStack) {
        super.execute(level, caster, wandStack);
        SpellHelper.spawnBurst(level, this, caster.getEyePosition(), 14, 0.24);
    }

    @Override
    public int getBaseEffectDurationTicks() {
        return 600;
    }

    @Override
    public float getProjectileSpeed() {
        return 0.0f;
    }
}
