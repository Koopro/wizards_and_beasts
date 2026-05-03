package at.koopro.wizardsandbeasts.spell.spells;

import at.koopro.wizardsandbeasts.spell.*;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;

public class Crucio extends Spell {

    public Crucio() {
        super("crucio", "Crucio", SpellCategory.DARK_ARTS, 220, 2.0f, 0xFFFF0044);
    }

    @Override
    protected SpellProperties buildProperties() {
        return SpellProperties.beamChannel(50.0f)
                .targetEffect(() -> new MobEffectInstance(MobEffects.WITHER, 40, 0, false, true, true))
                .targetEffect(() -> new MobEffectInstance(MobEffects.SLOWNESS, 40, 1, false, true, true))
                .sound(SoundEvents.BLAZE_SHOOT, 1.0f, 0.8f)
                .build();
    }

    @Override
    protected SpellRequirement buildRequirement() {
        return SpellRequirement.proficiency(Spells.INCENDIO, Proficiency.MASTERED);
    }
}
