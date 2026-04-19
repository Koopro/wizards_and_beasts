package at.koopro.neo.spell.spells;

import at.koopro.neo.spell.*;
import net.minecraft.sounds.SoundEvents;

public class Bombarda extends Spell {

    public Bombarda() {
        super("bombarda", "Bombarda", SpellCategory.COMBAT, 100, 8.0f, 0xFFFF8800);
    }

    @Override
    protected SpellProperties buildProperties() {
        return SpellProperties.targeted(20.0f)
                .explodes(3.0f, true)
                .sound(SoundEvents.BLAZE_SHOOT, 1.0f, 0.7f)
                .build();
    }

    @Override
    protected SpellRequirement buildRequirement() {
        return SpellRequirement.knows(Spells.DIFFINDO);
    }
}
