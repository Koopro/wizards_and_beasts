package at.koopro.wizardsandbeasts.effect;

import at.koopro.wizardsandbeasts.toad.PeppermintToad;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;
import org.jspecify.annotations.NonNull;

/**
 * The toad, still hopping, from the inside.
 *
 * <p>The whole effect is the joke, so unlike most of this mod's effects it does its own work on tick
 * rather than delegating to a handler: there is nothing here that needs an event, a player input or a
 * client render — just a small noise and a puff of green every three seconds.
 *
 * <p>The sound comes from the eater's own position at low volume, so it reads as coming from them
 * rather than from the world, and everyone nearby can hear it.
 */
public final class PeppermintHopEffect extends MobEffect {

    /** Mint green. */
    private static final int COLOUR = 0x7FD4A0;

    public PeppermintHopEffect() {
        super(MobEffectCategory.BENEFICIAL, COLOUR);
    }

    @Override
    public boolean isBeneficial() {
        return true;
    }

    @Override
    public boolean applyEffectTick(@NonNull ServerLevel level, @NonNull LivingEntity entity, int amplifier) {
        level.playSound(null, entity.getX(), entity.getY(), entity.getZ(),
                SoundEvents.FROG_LONG_JUMP, SoundSource.PLAYERS, 0.25f, 1.5f);
        level.sendParticles(ParticleTypes.HAPPY_VILLAGER,
                entity.getX(), entity.getY() + entity.getBbHeight() * 0.45, entity.getZ(),
                3, 0.2, 0.15, 0.2, 0.0);
        return true;
    }

    @Override
    public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) {
        return duration % PeppermintToad.HOP_INTERVAL == 0;
    }
}
