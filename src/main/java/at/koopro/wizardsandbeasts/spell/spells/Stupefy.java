package at.koopro.wizardsandbeasts.spell.spells;

import at.koopro.wizardsandbeasts.spell.*;
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
                .stuns()
                .targetEffect(() -> new MobEffectInstance(MobEffects.SLOWNESS, 40, 1))
                .targetEffect(() -> new MobEffectInstance(MobEffects.WEAKNESS, 50, 1))
                .sound(SoundEvents.BLAZE_SHOOT, 0.8f, 1.2f)
                .build();
    }

    @Override
    protected SpellRequirement buildRequirement() {
        return SpellRequirement.knows(Spells.FLIPENDO);
    }
}
