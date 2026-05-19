package at.koopro.wizardsandbeasts.spell.impl;

import at.koopro.wizardsandbeasts.effect.ModEffects;
import at.koopro.wizardsandbeasts.spell.core.*;
import at.koopro.wizardsandbeasts.spell.cast.*;
import at.koopro.wizardsandbeasts.spell.lib.*;
import at.koopro.wizardsandbeasts.spell.beam.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

public class Nox extends Spell {

    public Nox() {
        super("nox", "Nox", SpellCategory.UTILITY, 20, 0.0f, 0xFF222244);
    }

    @Override
    protected SpellProperties buildProperties() {
        return SpellProperties.self()
                .sound(SoundEvents.FIRE_EXTINGUISH, 0.5f, 1.5f)
                .build();
    }

    @Override
    protected SpellRequirement buildRequirement() {
        return SpellRequirement.knows(Spells.LUMOS);
    }

    @Override
    public void execute(ServerLevel level, ServerPlayer caster, ItemStack wandStack) {
        playSound(level, caster);
        caster.removeEffect(ModEffects.LUMOS_FIELD);
        Vec3 p = caster.getEyePosition();
        SpellHelper.spawnBurst(level, this, p, 14, 0.22);
    }
}
