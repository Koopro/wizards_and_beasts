package at.koopro.wizardsandbeasts.spell.spells;

import at.koopro.wizardsandbeasts.spell.*;
import net.minecraft.sounds.SoundEvents;

public class Diffindo extends Spell {

    public Diffindo() {
        super("diffindo", "Diffindo", SpellCategory.COMBAT, 50, 6.0f, 0xFFCC0000);
    }

    @Override
    protected SpellProperties buildProperties() {
        return SpellProperties.projectile()
                .sound(SoundEvents.BLAZE_SHOOT, 0.8f, 1.5f)
                .build();
    }

    @Override
    protected SpellRequirement buildRequirement() {
        return SpellRequirement.proficiency(Spells.STUPEFY, Proficiency.PROFICIENT);
    }
}
