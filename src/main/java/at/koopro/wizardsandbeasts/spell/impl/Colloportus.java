package at.koopro.wizardsandbeasts.spell.impl;

import at.koopro.wizardsandbeasts.spell.core.*;
import at.koopro.wizardsandbeasts.spell.cast.*;
import at.koopro.wizardsandbeasts.spell.lib.*;
import at.koopro.wizardsandbeasts.spell.beam.*;
import net.minecraft.sounds.SoundEvents;

public class Colloportus extends Spell {

    public Colloportus() {
        super("colloportus", "Colloportus", SpellCategory.UTILITY, 45, 0.0f, 0xFF6688CC);
    }

    @Override
    protected SpellProperties buildProperties() {
        return SpellProperties.targeted(5.0f)
                .opensBlocks()
                .sound(SoundEvents.IRON_DOOR_CLOSE, 0.75f, 1.15f)
                .build();
    }

    @Override
    protected SpellRequirement buildRequirement() {
        return SpellRequirement.knows(Spells.ALOHOMORA);
    }
}
