package at.koopro.neo.spell.spells;

import at.koopro.neo.spell.*;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;

public class Stupefy extends Spell {

    public Stupefy() {
        super("stupefy", "Stupefy", SpellCategory.COMBAT, 40, 4.0f, 0xFFFF0000);
    }

    @Override
    protected SpellProperties buildProperties() {
        return SpellProperties.projectile()
                .targetEffect(() -> new MobEffectInstance(MobEffects.SLOWNESS, 60, 4))
                .targetEffect(() -> new MobEffectInstance(MobEffects.WEAKNESS, 60, 2))
                .sound(SoundEvents.BLAZE_SHOOT, 0.8f, 1.2f)
                .build();
    }

    @Override
    protected SpellRequirement buildRequirement() {
        return SpellRequirement.knows(Spells.FLIPENDO);
    }
}
