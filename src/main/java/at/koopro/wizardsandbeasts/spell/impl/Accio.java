package at.koopro.wizardsandbeasts.spell.impl;

import at.koopro.wizardsandbeasts.spell.core.*;
import at.koopro.wizardsandbeasts.spell.cast.*;
import at.koopro.wizardsandbeasts.spell.lib.*;
import at.koopro.wizardsandbeasts.spell.beam.*;
import net.minecraft.sounds.SoundEvents;

public class Accio extends Spell {

    public Accio() {
        super("accio", "Accio", SpellCategory.UTILITY, 40, 0.0f, 0xFF88FF88);
    }

    @Override
    protected SpellProperties buildProperties() {
        return SpellProperties.cone(16.0f)
                .pullsTarget(2.0f)
                .sound(SoundEvents.ENDER_PEARL_THROW, 0.7f, 1.2f)
                .build();
    }

    @Override
    protected SpellRequirement buildRequirement() {
        return SpellRequirement.knows(Spells.WINGARDIUM_LEVIOSA);
    }

    @Override
    public float getProjectileSpeed() {
        return 2.0f;
    }
}
