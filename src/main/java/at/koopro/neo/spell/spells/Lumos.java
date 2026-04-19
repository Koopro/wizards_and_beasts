package at.koopro.neo.spell.spells;

import at.koopro.neo.spell.*;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;

public class Lumos extends Spell {

    public Lumos() {
        super("lumos", "Lumos", SpellCategory.UTILITY, 20, 0.0f, 0xFFFFFF88);
    }

    @Override
    protected SpellProperties buildProperties() {
        return SpellProperties.self()
                .selfEffect(() -> new MobEffectInstance(MobEffects.GLOWING, 600, 0, false, true, true))
                .selfEffect(() -> new MobEffectInstance(MobEffects.NIGHT_VISION, 600, 0, false, true, true))
                .sound(SoundEvents.EXPERIENCE_ORB_PICKUP, 0.5f, 1.5f)
                .build();
    }

    @Override
    protected SpellRequirement buildRequirement() {
        return SpellRequirement.none();
    }
}
