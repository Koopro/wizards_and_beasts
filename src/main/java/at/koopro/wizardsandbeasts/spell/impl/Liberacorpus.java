package at.koopro.wizardsandbeasts.spell.impl;

import at.koopro.wizardsandbeasts.spell.core.*;
import at.koopro.wizardsandbeasts.spell.cast.*;
import at.koopro.wizardsandbeasts.spell.lib.*;
import at.koopro.wizardsandbeasts.spell.beam.*;
import net.minecraft.sounds.SoundEvents;

public class Liberacorpus extends Spell {

    public Liberacorpus() {
        super("liberacorpus", "Liberacorpus", SpellCategory.UTILITY, 35, 0.0f, 0xFF88CCFF);
    }

    @Override
    protected SpellProperties buildProperties() {
        return SpellProperties.targeted(8.0f)
                .sound(SoundEvents.EXPERIENCE_ORB_PICKUP, 0.55f, 1.35f)
                .build();
    }

    @Override
    protected SpellRequirement buildRequirement() {
        return SpellRequirement.knows(Spells.WINGARDIUM_LEVIOSA);
    }
}
