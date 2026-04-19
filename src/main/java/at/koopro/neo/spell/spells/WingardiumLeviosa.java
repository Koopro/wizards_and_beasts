package at.koopro.neo.spell.spells;

import at.koopro.neo.spell.*;
import net.minecraft.sounds.SoundEvents;

public class WingardiumLeviosa extends Spell {

    public WingardiumLeviosa() {
        super("wingardium_leviosa", "Wingardium Leviosa", SpellCategory.UTILITY, 60, 0.0f, 0xFF88CCFF);
    }

    @Override
    protected SpellProperties buildProperties() {
        return SpellProperties.targeted(10.0f)
                .levitatesTarget(100)
                .sound(SoundEvents.ENDER_PEARL_THROW, 0.7f, 1.0f)
                .build();
    }

    @Override
    protected SpellRequirement buildRequirement() {
        return SpellRequirement.knows(Spells.LUMOS);
    }
}
