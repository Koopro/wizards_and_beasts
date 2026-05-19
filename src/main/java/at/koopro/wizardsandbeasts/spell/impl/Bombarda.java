package at.koopro.wizardsandbeasts.spell.impl;

import at.koopro.wizardsandbeasts.spell.core.*;
import at.koopro.wizardsandbeasts.spell.cast.*;
import at.koopro.wizardsandbeasts.spell.lib.*;
import at.koopro.wizardsandbeasts.spell.beam.*;
import net.minecraft.sounds.SoundEvents;

public class Bombarda extends Spell {

    public Bombarda() {
        super("bombarda", "Bombarda", SpellCategory.COMBAT, 140, 7.0f, 0xFFFF8800);
    }

    @Override
    protected SpellProperties buildProperties() {
        return SpellProperties.targeted(20.0f)
                .explodes(2.5f, false)
                .sound(SoundEvents.BLAZE_SHOOT, 1.0f, 0.7f)
                .build();
    }

    @Override
    protected SpellRequirement buildRequirement() {
        return SpellRequirement.knows(Spells.DIFFINDO);
    }
}
