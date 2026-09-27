package at.koopro.wizardsandbeasts.effect;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.jspecify.annotations.NonNull;

/**
 * The cold a Dementor carries, by band (see {@code DementorAura}).
 *
 * <ul>
 *   <li><b>0 — cold</b> (within 16): nothing but the chill itself — the grey vignette and frost. No harm.</li>
 *   <li><b>1 — fear</b> (within 8): slowed a little, hungrier; the memory voices start.</li>
 *   <li><b>2 — drain</b> (within 4): slow and weak, and a sliver of health every four seconds.</li>
 *   <li><b>3 — a swarm at arm's length</b>: slower, weaker, a sliver every two seconds.</li>
 * </ul>
 *
 * <p>The chill wears you down; it never kills. It stops taking health at {@link #HEALTH_FLOOR}: what a Dementor
 * finishes, it finishes with the Kiss. The old ladder hurt at every band, including 24 blocks off.
 */
public final class DementorChillEffect extends MobEffect {

    /** The chill takes no health below this. */
    public static final float HEALTH_FLOOR = 4.0f;

    public DementorChillEffect() {
        super(MobEffectCategory.HARMFUL, 0x4A5A8A);
    }

    @Override
    public boolean applyEffectTick(@NonNull ServerLevel level, @NonNull LivingEntity entity, int amplifier) {
        if (amplifier >= 1) {   // durations outlast the one-second tick, so these hold steady while chilled
            entity.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 42, amplifier - 1, false, false, true));
            if (entity instanceof Player player) {
                player.getFoodData().addExhaustion(0.1f * amplifier);
            }
        }
        if (amplifier >= 2) {
            entity.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 42, amplifier - 2, false, false, true));
            // Runs once a second; the tick window picks one second in four (drain) or two (swarm).
            boolean pulse = entity.tickCount % (amplifier >= 3 ? 40 : 80) < 20;
            if (pulse && entity.getHealth() > HEALTH_FLOOR) {
                entity.hurtServer(level, level.damageSources().magic(), 1.0f);
            }
        }
        return true;
    }

    @Override
    public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) {
        // Once a second. The aura refreshes the duration every second, so a longer modulus would fire unevenly.
        return duration % 20 == 0;
    }

    @Override
    public boolean isBeneficial() {
        return false;
    }
}
