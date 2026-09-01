package at.koopro.wizardsandbeasts.effect;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;
import org.jspecify.annotations.NonNull;

/**
 * The three seconds after a mouthful of Firewhisky, while it is still going down.
 *
 * <p>Two jobs. It drains a little hunger — the burn has to <em>cost</em> something or the Strength is
 * free — and it is what the client reads to draw the heat haze, so the distortion needs no packet of
 * its own and ends exactly when the burn does.
 */
public final class FirewhiskyBurnEffect extends MobEffect {

    /** Ticks between bites of hunger. Three over the three seconds. */
    private static final int DRAIN_INTERVAL = 20;

    /** Exhaustion per bite. Enough to notice on a full bar, not enough to starve anyone. */
    private static final float EXHAUSTION = 3.0f;

    /** Ember orange. */
    private static final int COLOUR = 0xD9541E;

    public FirewhiskyBurnEffect() {
        super(MobEffectCategory.HARMFUL, COLOUR);
    }

    @Override
    public boolean applyEffectTick(@NonNull ServerLevel level, @NonNull LivingEntity entity, int amplifier) {
        if (entity instanceof net.minecraft.world.entity.player.Player player) {
            player.causeFoodExhaustion(EXHAUSTION);
        }
        return true;
    }

    @Override
    public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) {
        return duration % DRAIN_INTERVAL == 0;
    }
}
