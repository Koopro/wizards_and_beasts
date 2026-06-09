package at.koopro.wizardsandbeasts.spell.impl;

import at.koopro.wizardsandbeasts.spell.core.*;
import at.koopro.wizardsandbeasts.spell.cast.*;
import at.koopro.wizardsandbeasts.spell.lib.*;
import at.koopro.wizardsandbeasts.spell.beam.*;
import net.minecraft.sounds.SoundEvents;

public class Diffindo extends Spell {

    public Diffindo() {
        super("diffindo", "Diffindo", SpellCategory.COMBAT, 50, 6.0f, 0xFFCC0000);
    }

    @Override
    protected SpellProperties buildProperties() {
        return SpellProperties.projectile()
                .sound(SoundEvents.BLAZE_SHOOT, 0.8f, 1.5f)
                .build();
    }

    @Override
    protected SpellRequirement buildRequirement() {
        return SpellRequirement.proficiency("stupefy", Proficiency.PROFICIENT);
    }
}
