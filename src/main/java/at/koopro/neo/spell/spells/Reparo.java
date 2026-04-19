package at.koopro.neo.spell.spells;

import at.koopro.neo.spell.*;
import net.minecraft.sounds.SoundEvents;

public class Reparo extends Spell {

    public Reparo() {
        super("reparo", "Reparo", SpellCategory.UTILITY, 80, 0.0f, 0xFF88FFCC);
    }

    @Override
    protected SpellProperties buildProperties() {
        return SpellProperties.self()
                .repairsItem(100)
                .sound(SoundEvents.EXPERIENCE_ORB_PICKUP, 0.5f, 1.0f)
                .build();
    }

    @Override
    protected SpellRequirement buildRequirement() {
        return SpellRequirement.knows(Spells.LUMOS);
    }
}
