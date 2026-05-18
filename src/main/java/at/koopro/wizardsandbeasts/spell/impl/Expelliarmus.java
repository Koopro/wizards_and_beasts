package at.koopro.wizardsandbeasts.spell.impl;

import at.koopro.wizardsandbeasts.spell.core.*;
import at.koopro.wizardsandbeasts.spell.cast.*;
import at.koopro.wizardsandbeasts.spell.lib.*;
import at.koopro.wizardsandbeasts.spell.beam.*;

public class Expelliarmus extends Spell {

    public Expelliarmus() {
        super("expelliarmus", "Expelliarmus", SpellCategory.COMBAT, 50, 0.0f, 0xFFFF6600);
    }

    @Override
    protected SpellProperties buildProperties() {
        return SpellProperties.projectile()
                .disarms()
                .build();
    }

    @Override
    protected SpellRequirement buildRequirement() {
        return SpellRequirement.knows(Spells.STUPEFY);
    }

    @Override
    public float getProjectileSpeed() {
        return 1.6f;
    }

    @Override
    public float getProjectileSpread() {
        return 0.02f;
    }

    @Override
    public float getBaseKnockback() {
        return 1.2f;
    }
}
