package at.koopro.wizardsandbeasts.spell.impl;

import at.koopro.wizardsandbeasts.spell.core.*;
import at.koopro.wizardsandbeasts.spell.cast.*;
import at.koopro.wizardsandbeasts.spell.lib.*;
import at.koopro.wizardsandbeasts.spell.beam.*;
import net.minecraft.sounds.SoundEvents;

public class Flipendo extends Spell {

    public Flipendo() {
        super("flipendo", "Flipendo", SpellCategory.COMBAT, 38, 2.0f, 0xFF00CCFF);
    }

    @Override
    protected SpellProperties buildProperties() {
        return SpellProperties.projectile()
                .knockback(3.0f)
                .sound(SoundEvents.BLAZE_SHOOT, 0.8f, 1.5f)
                .build();
    }

    @Override
    protected SpellRequirement buildRequirement() {
        return SpellRequirement.none();
    }
}
