package at.koopro.neo.spell.spells;

import at.koopro.neo.spell.*;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;

public class ArrestoMomentum extends Spell {

    public ArrestoMomentum() {
        super("arresto_momentum", "Arresto Momentum", SpellCategory.UTILITY, 60, 0.0f, 0xFF88AAFF);
    }

    @Override
    protected SpellProperties buildProperties() {
        return SpellProperties.self()
                .selfEffect(() -> new MobEffectInstance(MobEffects.SLOW_FALLING, 200, 0, false, true, true))
                .sound(SoundEvents.ENDER_PEARL_THROW, 0.7f, 0.5f)
                .build();
    }

    @Override
    protected SpellRequirement buildRequirement() {
        return SpellRequirement.knows(Spells.WINGARDIUM_LEVIOSA);
    }
}
