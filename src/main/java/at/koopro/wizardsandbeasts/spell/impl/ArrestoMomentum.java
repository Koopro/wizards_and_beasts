package at.koopro.wizardsandbeasts.spell.impl;

import at.koopro.wizardsandbeasts.spell.core.*;
import at.koopro.wizardsandbeasts.spell.cast.*;
import at.koopro.wizardsandbeasts.spell.lib.*;
import at.koopro.wizardsandbeasts.spell.beam.*;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;

public class ArrestoMomentum extends Spell {

    public ArrestoMomentum() {
        super("arresto_momentum", "Arresto Momentum", SpellCategory.UTILITY, 80, 0.0f, 0xFF88AAFF);
    }

    @Override
    protected SpellProperties buildProperties() {
        return SpellProperties.self()
                .selfEffect(() -> new MobEffectInstance(MobEffects.SLOW_FALLING, 220, 0, false, true, true))
                .sound(SoundEvents.ENDER_PEARL_THROW, 0.7f, 0.5f)
                .build();
    }

    @Override
    protected SpellRequirement buildRequirement() {
        return SpellRequirement.knows(Spells.WINGARDIUM_LEVIOSA);
    }
}
