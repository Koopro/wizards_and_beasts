package at.koopro.neo.spell.spells;

import at.koopro.neo.spell.*;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;

public class ExpectoPatronum extends Spell {

    public ExpectoPatronum() {
        super("expecto_patronum", "Expecto Patronum", SpellCategory.DEFENSE, 200, 5.0f, 0xFFCCDDFF);
    }

    @Override
    protected SpellProperties buildProperties() {
        return SpellProperties.cone(8.0f)
                .undeadBonus(10.0f)
                .knockback(2.0f)
                .targetEffect(() -> new MobEffectInstance(MobEffects.GLOWING, 200, 0))
                .sound(SoundEvents.EXPERIENCE_ORB_PICKUP, 1.0f, 0.5f)
                .build();
    }

    @Override
    protected SpellRequirement buildRequirement() {
        return SpellRequirement.proficiency(Spells.PROTEGO, Proficiency.PROFICIENT);
    }
}
