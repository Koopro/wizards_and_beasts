package at.koopro.wizardsandbeasts.spell.impl;

import at.koopro.wizardsandbeasts.spell.core.*;
import at.koopro.wizardsandbeasts.spell.cast.*;
import at.koopro.wizardsandbeasts.spell.lib.*;
import at.koopro.wizardsandbeasts.spell.beam.*;
import net.minecraft.sounds.SoundEvents;

public class Confringo extends Spell {

    public Confringo() {
        super("confringo", "Confringo", SpellCategory.COMBAT, 80, 6.0f, 0xFFFF5500);
    }

    @Override
    protected SpellProperties buildProperties() {
        return SpellProperties.projectile()
                .ignites(3)
                .explodes(2.0f)
                .sound(SoundEvents.FIRECHARGE_USE, 1.0f, 0.8f)
                .build();
    }

    @Override
    protected SpellRequirement buildRequirement() {
        return SpellRequirement.knows(Spells.INCENDIO);
    }
}
