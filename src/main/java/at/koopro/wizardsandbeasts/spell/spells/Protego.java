package at.koopro.wizardsandbeasts.spell.spells;

import at.koopro.wizardsandbeasts.event.ProtegoShieldHandler;
import at.koopro.wizardsandbeasts.spell.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.ItemStack;

public class Protego extends Spell {
    public static final int PROTEGO_DURATION_TICKS = 100;

    public Protego() {
        super("protego", "Protego", SpellCategory.DEFENSE, 100, 0.0f, 0xFF4488FF);
    }

    @Override
    protected SpellProperties buildProperties() {
        return SpellProperties.self()
                .selfEffect(() -> new MobEffectInstance(MobEffects.RESISTANCE, PROTEGO_DURATION_TICKS, 1, false, true, true))
                .selfEffect(() -> new MobEffectInstance(MobEffects.ABSORPTION, PROTEGO_DURATION_TICKS, 1, false, true, true))
                .sound(SoundEvents.SHIELD_BLOCK.value(), 1.0f, 1.2f)
                .build();
    }

    @Override
    public void execute(ServerLevel level, ServerPlayer caster, ItemStack wandStack) {
        super.execute(level, caster, wandStack);
        ProtegoShieldHandler.activate(caster, level.getGameTime() + PROTEGO_DURATION_TICKS);
        if (getProficiency(caster) == Proficiency.MASTERED) {
            caster.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, 60, 2, false, true, true));
        }
    }

    @Override
    protected SpellRequirement buildRequirement() {
        return SpellRequirement.knows(Spells.EXPELLIARMUS);
    }
}
