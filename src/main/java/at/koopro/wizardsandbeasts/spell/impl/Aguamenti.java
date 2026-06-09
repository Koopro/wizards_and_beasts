package at.koopro.wizardsandbeasts.spell.impl;

import at.koopro.wizardsandbeasts.spell.core.*;
import at.koopro.wizardsandbeasts.spell.cast.*;
import at.koopro.wizardsandbeasts.spell.lib.*;
import at.koopro.wizardsandbeasts.spell.beam.*;
import net.minecraft.sounds.SoundEvents;

/**
 * Channeled water jet: soak farmland, fill cauldrons while aiming, and place a still water
 * block after holding the beam on a valid air target.
 */
public class Aguamenti extends Spell {

    public Aguamenti() {
        super("aguamenti", "Aguamenti", SpellCategory.UTILITY, 100, 0.0f, 0xFF33AAFF);
    }

    @Override
    protected SpellProperties buildProperties() {
        return SpellProperties.beamChannel(12.0f)
                .sound(SoundEvents.BUCKET_EMPTY, 0.35f, 1.1f)
                .build();
    }

    @Override
    protected SpellRequirement buildRequirement() {
        return SpellRequirement.knows("lumos");
    }

    @Override
    public float getProjectileSpeed() {
        return 1.3f;
    }

    @Override
    public float getProjectileSpread() {
        return 0.08f;
    }
}
