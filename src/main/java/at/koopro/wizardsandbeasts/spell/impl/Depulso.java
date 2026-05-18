package at.koopro.wizardsandbeasts.spell.impl;

import at.koopro.wizardsandbeasts.spell.core.*;
import at.koopro.wizardsandbeasts.spell.cast.*;
import at.koopro.wizardsandbeasts.spell.lib.*;
import at.koopro.wizardsandbeasts.spell.beam.*;
import net.minecraft.sounds.SoundEvents;

public class Depulso extends Spell {

    public Depulso() {
        super("depulso", "Depulso", SpellCategory.COMBAT, 40, 1.0f, 0xFF0088FF);
    }

    @Override
    protected SpellProperties buildProperties() {
        return SpellProperties.projectile()
                .knockback(5.0f)
                .sound(SoundEvents.BLAZE_SHOOT, 0.8f, 0.9f)
                .build();
    }

    @Override
    protected SpellRequirement buildRequirement() {
        return SpellRequirement.proficiency(Spells.FLIPENDO, Proficiency.PROFICIENT);
    }
}
