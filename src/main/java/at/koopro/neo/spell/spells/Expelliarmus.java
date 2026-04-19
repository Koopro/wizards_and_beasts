package at.koopro.neo.spell.spells;

import at.koopro.neo.spell.*;
import net.minecraft.sounds.SoundEvents;

public class Expelliarmus extends Spell {

    public Expelliarmus() {
        super("expelliarmus", "Expelliarmus", SpellCategory.COMBAT, 60, 0.0f, 0xFFFF6600);
    }

    @Override
    protected SpellProperties buildProperties() {
        return SpellProperties.projectile()
                .disarms()
                .sound(SoundEvents.BLAZE_SHOOT, 0.8f, 1.4f)
                .build();
    }

    @Override
    protected SpellRequirement buildRequirement() {
        return SpellRequirement.knows(Spells.STUPEFY);
    }
}
