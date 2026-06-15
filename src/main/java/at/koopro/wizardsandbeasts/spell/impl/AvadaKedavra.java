package at.koopro.wizardsandbeasts.spell.impl;

import at.koopro.wizardsandbeasts.spell.core.*;
import at.koopro.wizardsandbeasts.spell.cast.*;
import at.koopro.wizardsandbeasts.spell.lib.*;
import at.koopro.wizardsandbeasts.spell.beam.*;
import net.minecraft.sounds.SoundEvents;

public class AvadaKedavra extends Spell {

    public AvadaKedavra() {
        super("avada_kedavra", "Avada Kedavra", SpellCategory.DARK_ARTS, 1200, 999.0f, 0xFF00FF00);
    }

    @Override
    protected SpellProperties buildProperties() {
        // "A jet of green light" — a cold magical incantation, not a fiery blaze shot.
        return SpellProperties.beamLethal(50.0f)
                .sound(SoundEvents.EVOKER_CAST_SPELL, 1.0f, 0.5f)
                .build();
    }

    @Override
    protected SpellRequirement buildRequirement() {
        // Canon: the three Unforgivable Curses are independent — none is mastered as a prerequisite of
        // another. Gate the Killing Curse behind command of the other two Unforgivables instead.
        // crucio is a JSON spell now — id-string requirement (resolved lazily at isMet time).
        return SpellRequirement.allOf(
                SpellRequirement.proficiency(Spells.IMPERIO, Proficiency.PROFICIENT),
                SpellRequirement.proficiency("crucio", Proficiency.PROFICIENT));
    }

    @Override
    public boolean isUnblockable() {
        return true;
    }

    @Override
    public float getProjectileSpeed() {
        return 2.2f;
    }
}
