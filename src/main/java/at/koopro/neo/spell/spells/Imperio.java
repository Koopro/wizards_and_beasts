package at.koopro.neo.spell.spells;

import at.koopro.neo.spell.*;
import net.minecraft.sounds.SoundEvents;

public class Imperio extends Spell {

    public Imperio() {
        super("imperio", "Imperio", SpellCategory.DARK_ARTS, 200, 0.0f, 0xFF9900FF);
    }

    @Override
    protected SpellProperties buildProperties() {
        return SpellProperties.targeted(8.0f)
                .controlsMob(400)
                .sound(SoundEvents.ENDER_PEARL_THROW, 0.8f, 0.5f)
                .build();
    }

    @Override
    protected SpellRequirement buildRequirement() {
        return SpellRequirement.proficiency(Spells.STUPEFY, Proficiency.MASTERED);
    }
}
