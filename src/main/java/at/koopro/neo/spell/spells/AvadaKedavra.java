package at.koopro.neo.spell.spells;

import at.koopro.neo.spell.*;
import net.minecraft.sounds.SoundEvents;

public class AvadaKedavra extends Spell {

    public AvadaKedavra() {
        super("avada_kedavra", "Avada Kedavra", SpellCategory.DARK_ARTS, 1200, 999.0f, 0xFF00FF00);
    }

    @Override
    protected SpellProperties buildProperties() {
        return SpellProperties.projectile()
                .sound(SoundEvents.BLAZE_SHOOT, 1.0f, 0.5f)
                .build();
    }

    @Override
    protected SpellRequirement buildRequirement() {
        return SpellRequirement.proficiency(Spells.CRUCIO, Proficiency.PROFICIENT);
    }
}
