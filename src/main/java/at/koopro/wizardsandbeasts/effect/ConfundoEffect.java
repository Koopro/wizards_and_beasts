package at.koopro.wizardsandbeasts.effect;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;
import org.jspecify.annotations.NonNull;

public final class ConfundoEffect extends MobEffect {
    public ConfundoEffect() {
        super(MobEffectCategory.HARMFUL, 0x6A0DAD);
    }

    @Override
    public boolean applyEffectTick(@NonNull ServerLevel level, @NonNull LivingEntity entity, int amplifier) {
        // A player sees the world swim (client overlay: {@link at.koopro.wizardsandbeasts.client.hud.MobEffectFullscreenOverlays}).
        // A creature has no screen to swim, so it loses the thread instead: now and then it forgets what it was after
        // and stops where it stands. Without this Confundo did nothing at all to anything that was not a player.
        if (entity instanceof net.minecraft.world.entity.Mob mob && level.random.nextFloat() < 0.5f) {
            mob.setTarget(null);
            mob.getNavigation().stop();
        }
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
}
