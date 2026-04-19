package at.koopro.neo.spell.spells;

import at.koopro.neo.spell.*;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;

public class Protego extends Spell {

    public Protego() {
        super("protego", "Protego", SpellCategory.DEFENSE, 100, 0.0f, 0xFF4488FF);
    }

    @Override
    protected SpellProperties buildProperties() {
        return SpellProperties.self()
                .selfEffect(() -> new MobEffectInstance(MobEffects.RESISTANCE, 100, 1, false, true, true))
                .selfEffect(() -> new MobEffectInstance(MobEffects.ABSORPTION, 100, 1, false, true, true))
                .sound(SoundEvents.SHIELD_BLOCK.value(), 1.0f, 1.2f)
                .build();
    }

    @Override
    protected SpellRequirement buildRequirement() {
        return SpellRequirement.knows(Spells.EXPELLIARMUS);
    }
}
