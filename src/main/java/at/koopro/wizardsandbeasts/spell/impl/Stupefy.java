package at.koopro.wizardsandbeasts.spell.impl;

import at.koopro.wizardsandbeasts.effect.ModEffects;
import at.koopro.wizardsandbeasts.spell.core.*;
import at.koopro.wizardsandbeasts.spell.cast.*;
import at.koopro.wizardsandbeasts.spell.lib.*;
import at.koopro.wizardsandbeasts.spell.beam.*;
import net.minecraft.world.effect.MobEffectInstance;

public class Stupefy extends Spell {

    public Stupefy() {
        super("stupefy", "Stupefy", SpellCategory.COMBAT, 40, 4.0f, 0xFFFF0000);
    }

    @Override
    protected SpellProperties buildProperties() {
        return SpellProperties.projectile()
                .stuns()
                .targetEffect(() -> new MobEffectInstance(ModEffects.STUPEFY, 100, 0, false, true, true))
                .build();
    }

    @Override
    protected SpellRequirement buildRequirement() {
        return SpellRequirement.knows(Spells.FLIPENDO);
    }

    @Override
    public int getBaseEffectDurationTicks() {
        return 100;
    }

    @Override
    public float getProjectileSpeed() {
        return 1.8f;
    }

    @Override
    public float getProjectileSpread() {
        return 0.04f;
    }

    @Override
    public float getBaseKnockback() {
        return 0.5f;
    }
}
