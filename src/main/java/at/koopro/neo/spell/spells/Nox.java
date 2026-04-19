package at.koopro.neo.spell.spells;

import at.koopro.neo.spell.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.ItemStack;

public class Nox extends Spell {

    public Nox() {
        super("nox", "Nox", SpellCategory.UTILITY, 20, 0.0f, 0xFF222244);
    }

    @Override
    protected SpellProperties buildProperties() {
        return SpellProperties.self()
                .sound(SoundEvents.FIRE_EXTINGUISH, 0.5f, 1.5f)
                .build();
    }

    @Override
    protected SpellRequirement buildRequirement() {
        return SpellRequirement.knows(Spells.LUMOS);
    }

    @Override
    public void execute(ServerLevel level, ServerPlayer caster, ItemStack wandStack) {
        playSound(level, caster);
        caster.removeEffect(MobEffects.GLOWING);
        caster.removeEffect(MobEffects.NIGHT_VISION);
    }
}
