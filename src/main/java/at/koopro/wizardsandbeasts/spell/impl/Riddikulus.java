package at.koopro.wizardsandbeasts.spell.impl;

import at.koopro.wizardsandbeasts.registry.ModSounds;
import at.koopro.wizardsandbeasts.spell.core.*;
import at.koopro.wizardsandbeasts.spell.cast.*;
import at.koopro.wizardsandbeasts.spell.lib.*;
import at.koopro.wizardsandbeasts.spell.beam.*;

/**
 * Boggart counter-curse — placeholder until a Boggart entity exists.
 * Cast burst is handled in {@link at.koopro.wizardsandbeasts.spell.SpellExecutor} self-utility rules.
 */
public class Riddikulus extends Spell {

    public Riddikulus() {
        super("riddikulus", "Riddikulus", SpellCategory.DEFENSE, 60, 0.0f, 0xFFFFCC66);
    }

    @Override
    protected SpellProperties buildProperties() {
        return SpellProperties.self()
                .sound(ModSounds.SPELL_CAST_CHARM.get(), 0.65f, 1.25f)
                .build();
    }

    @Override
    protected SpellRequirement buildRequirement() {
        return SpellRequirement.none();
    }
}
