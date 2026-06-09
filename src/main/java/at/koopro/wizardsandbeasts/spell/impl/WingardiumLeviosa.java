package at.koopro.wizardsandbeasts.spell.impl;

import at.koopro.wizardsandbeasts.spell.core.*;
import at.koopro.wizardsandbeasts.spell.cast.*;
import at.koopro.wizardsandbeasts.spell.lib.*;
import at.koopro.wizardsandbeasts.spell.beam.*;
import net.minecraft.sounds.SoundEvents;

public class WingardiumLeviosa extends Spell {

    public WingardiumLeviosa() {
        super("wingardium_leviosa", "Wingardium Leviosa", SpellCategory.UTILITY, 60, 0.0f, 0xFF88CCFF);
    }

    @Override
    protected SpellProperties buildProperties() {
        return SpellProperties.beamChannel(16.0f)
                .sound(SoundEvents.ENDER_PEARL_THROW, 0.7f, 1.0f)
                .build();
    }

    @Override
    protected SpellRequirement buildRequirement() {
        return SpellRequirement.knows("lumos");
    }

    @Override
    public float getProjectileSpeed() {
        return 0.0f;
    }
}
