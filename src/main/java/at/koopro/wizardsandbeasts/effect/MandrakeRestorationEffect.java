package at.koopro.wizardsandbeasts.effect;

import at.koopro.wizardsandbeasts.spell.petrify.PetrifyServerLogic;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;

/**
 * Applied by drinking the Mandrake Restoration Draught. A momentary effect — its only purpose is the
 * {@link #onEffectAdded} hook, which cures basilisk-gaze petrification. The brew system
 * ({@link at.koopro.wizardsandbeasts.brew.def.BrewDefinition}) only knows how to apply mob effects, so
 * the cure is implemented as one rather than adding a bespoke "on drink" hook to the brew pipeline.
 */
public final class MandrakeRestorationEffect extends MobEffect {

    public MandrakeRestorationEffect() {
        super(MobEffectCategory.BENEFICIAL, 0x6B8E4E);
    }

    @Override
    public boolean isBeneficial() {
        return true;
    }

    @Override
    public void onEffectAdded(LivingEntity entity, int amplifier) {
        if (entity instanceof ServerPlayer player && player.level() instanceof ServerLevel level) {
            PetrifyServerLogic.cure(level, player);
        }
    }
}
