package at.koopro.wizardsandbeasts.spell.spells;

import at.koopro.wizardsandbeasts.spell.*;
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
        return SpellRequirement.proficiency(Spells.STUPEFY, Proficiency.PROFICIENT);
    }
}
