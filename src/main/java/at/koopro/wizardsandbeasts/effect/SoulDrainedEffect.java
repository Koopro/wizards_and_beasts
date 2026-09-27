package at.koopro.wizardsandbeasts.effect;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.MobEffectEvent;
import org.jspecify.annotations.NonNull;

/**
 * What a Dementor's Kiss leaves: the body without the soul. Canon: "you'll just exist… an empty shell", and there is
 * no coming back from it. So the effect holds the body down — slow, weak, sightless — and the body fails, a heart a
 * second, until it dies.
 *
 * <p>Nothing cures it while the victim lives ({@link Permanence}): not milk, not chocolate, not a Phoenix's song. The
 * old effect washed off with a bucket of milk. Death clears it as it clears every effect.
 */
public final class SoulDrainedEffect extends MobEffect {

    public SoulDrainedEffect() {
        super(MobEffectCategory.HARMFUL, 0xD8D8D8);
    }

    @Override
    public boolean applyEffectTick(@NonNull ServerLevel level, @NonNull LivingEntity entity, int amplifier) {
        entity.addEffect(new MobEffectInstance(MobEffects.SLOWNESS,    44, 3, false, true, true));
        entity.addEffect(new MobEffectInstance(MobEffects.BLINDNESS,   44, 0, false, true, true));
        entity.addEffect(new MobEffectInstance(MobEffects.WEAKNESS,    44, 2, false, true, true));
        entity.addEffect(new MobEffectInstance(MobEffects.MINING_FATIGUE, 44, 2, false, true, true));
        // Soul consumed — attrition damage bypasses armor
        entity.hurtServer(level, level.damageSources().magic(), 1.0f);
        return true;
    }

    @Override
    public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) {
        return duration % 20 == 0;
    }

    @Override
    public boolean isBeneficial() {
        return false;
    }

    /** Refuses every removal of {@code SOUL_DRAINED} from a living victim — milk, chocolate, commands alike. */
    @EventBusSubscriber(modid = WizardsAndBeastsMod.MODID)
    public static final class Permanence {

        private Permanence() {}

        @SubscribeEvent
        public static void onRemove(MobEffectEvent.Remove event) {
            // getEffect(), not getEffectInstance(): removal by type alone carries no instance.
            if (isPermanent(event.getEntity(), event.getEffect())) {
                event.setCanceled(true);
            }
        }

        static boolean isPermanent(LivingEntity entity, Holder<MobEffect> effect) {
            return effect.value() == ModEffects.SOUL_DRAINED.get() && entity.isAlive();
        }
    }
}
