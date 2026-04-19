package at.koopro.neo.spell.spells;

import at.koopro.neo.spell.*;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;

public class Crucio extends Spell {

    public Crucio() {
        super("crucio", "Crucio", SpellCategory.DARK_ARTS, 160, 2.0f, 0xFFFF0044);
    }

    @Override
    protected SpellProperties buildProperties() {
        return SpellProperties.projectile()
                .targetEffect(() -> new MobEffectInstance(MobEffects.WITHER, 200, 1))
                .targetEffect(() -> new MobEffectInstance(MobEffects.SLOWNESS, 200, 2))
                .sound(SoundEvents.BLAZE_SHOOT, 1.0f, 0.8f)
                .build();
    }

    @Override
    protected SpellRequirement buildRequirement() {
        return SpellRequirement.proficiency(Spells.INCENDIO, Proficiency.MASTERED);
    }
}
