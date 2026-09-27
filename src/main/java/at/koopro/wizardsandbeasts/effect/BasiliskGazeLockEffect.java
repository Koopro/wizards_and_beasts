package at.koopro.wizardsandbeasts.effect;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import org.jspecify.annotations.NonNull;

/**
 * The telegraph of a basilisk's stare taking hold: an escalating heartbeat, and the fullscreen overlay
 * ({@code MobEffectFullscreenOverlays} reads the remaining duration as progress). Amplifier 1 = direct sight (death
 * ahead), 0 = indirect (petrification ahead).
 *
 * <p>It decides nothing. {@code DeathGazeGoal} holds the eye contact, re-measures it every interval, removes this
 * effect the moment contact breaks, and applies the outcome itself when the windup completes. (The outcome used to
 * resolve on this effect's natural expiry, which never re-checked whether the victim had since broken line of sight.)
 */
public final class BasiliskGazeLockEffect extends MobEffect {

    public BasiliskGazeLockEffect() {
        super(MobEffectCategory.HARMFUL, 0x2E2416);
    }

    @Override
    public boolean isBeneficial() {
        return false;
    }

    @Override
    public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) {
        return duration % 4 == 0;
    }

    @Override
    public boolean applyEffectTick(@NonNull ServerLevel level, @NonNull LivingEntity entity, int amplifier) {
        MobEffectInstance instance = entity.getEffect(ModEffects.BASILISK_GAZE_LOCK);
        int remaining = instance == null ? 0 : instance.getDuration();
        float progress = 1.0f - Math.min(1.0f, Math.max(0.0f, remaining / (float) WINDUP_TICKS));
        float pitch = 0.6f + progress * 0.8f;
        level.playSound(null, entity.blockPosition(), SoundEvents.WARDEN_HEARTBEAT, SoundSource.HOSTILE, 0.6f, pitch);
        return true;
    }

    public static final int WINDUP_TICKS = 20;
}
