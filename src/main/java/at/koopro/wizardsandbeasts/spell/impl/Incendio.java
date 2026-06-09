package at.koopro.wizardsandbeasts.spell.impl;

import at.koopro.wizardsandbeasts.spell.core.*;
import at.koopro.wizardsandbeasts.spell.cast.*;
import at.koopro.wizardsandbeasts.spell.lib.*;
import at.koopro.wizardsandbeasts.spell.beam.*;
import net.minecraft.sounds.SoundEvents;

public class Incendio extends Spell {

    public Incendio() {
        super("incendio", "Incendio", SpellCategory.COMBAT, 80, 3.0f, 0xFFFF4400);
    }

    @Override
    protected SpellProperties buildProperties() {
        return SpellProperties.cone(4.0f)
                .ignites(5)
                .sound(SoundEvents.FIRECHARGE_USE, 1.0f, 1.0f)
                .build();
    }

    @Override
    protected SpellRequirement buildRequirement() {
        // TODO(effects): Apply ModEffects.SECTUMSEMPRA_BLEED from the future Sectumsempra spell.
        return SpellRequirement.proficiency("stupefy", Proficiency.PROFICIENT);
    }

    @Override
    public int getBaseEffectDurationTicks() {
        return 100;
    }

    @Override
    public float getProjectileSpeed() {
        return 1.4f;
    }

    @Override
    public float getProjectileSpread() {
        return 0.06f;
    }

    @Override
    public float getBaseKnockback() {
        return 0.2f;
    }
}
