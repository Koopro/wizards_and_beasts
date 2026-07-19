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
 * Windup applied by {@code DeathGazeGoal} for a lethal-gaze creature (the basilisk) instead of
 * triggering death/petrification immediately. Amplifier 0 = glancing gaze (petrify on expiry),
 * amplifier 1 = met gaze (instant death on expiry) — see {@code BasiliskGazeWindupHandler}, which
 * fires on natural {@code MobEffectEvent.Expired}, mirroring {@code ProtegoWardManager}'s idiom.
 * The escalating heartbeat here is the player-facing telegraph; a Blindfold/Blindness/Protego check
 * happening after expiry (not just at apply-time) lets a player avert their eyes mid-windup to
 * cancel the outcome.
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

    public static final int WINDUP_TICKS = 16;
}
