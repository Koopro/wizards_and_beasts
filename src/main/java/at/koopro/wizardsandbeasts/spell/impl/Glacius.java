package at.koopro.wizardsandbeasts.spell.impl;

import at.koopro.wizardsandbeasts.effect.ModEffects;
import at.koopro.wizardsandbeasts.spell.core.*;
import at.koopro.wizardsandbeasts.spell.cast.*;
import at.koopro.wizardsandbeasts.spell.lib.*;
import at.koopro.wizardsandbeasts.spell.beam.*;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;

public class Glacius extends Spell {

    public Glacius() {
        super("glacius", "Glacius", SpellCategory.COMBAT, 70, 2.0f, 0xFF88DDFF);
    }

    @Override
    protected SpellProperties buildProperties() {
        return SpellProperties.cone(4.0f)
                .targetEffect(() -> new MobEffectInstance(ModEffects.PETRIFICUS_TOTALUS, 40, 0, false, true, true))
                .sound(SoundEvents.ENDER_PEARL_THROW, 1.0f, 1.8f)
                .build();
    }

    @Override
    protected SpellRequirement buildRequirement() {
        return SpellRequirement.proficiency(Spells.FLIPENDO, Proficiency.PROFICIENT);
    }
}
