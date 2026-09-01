package at.koopro.wizardsandbeasts.whizzbee;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.effect.ModEffects;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingEvent;
import net.neoforged.neoforge.event.entity.living.MobEffectEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import org.jspecify.annotations.NullMarked;

/**
 * The fizz, the whizz, and the pop.
 *
 * <p>Three things a {@link net.minecraft.world.effect.MobEffect} cannot do for itself:
 *
 * <ul>
 *   <li><b>The whizz</b> — {@link LivingEvent.LivingJumpEvent} fires <em>after</em> vanilla has set
 *       the jump velocity, including whatever Jump Boost contributed. Adding to it there is what
 *       makes the kick compound with the boost instead of replacing it.</li>
 *   <li><b>The fizz</b> — a steady sputter at the feet, spawned server-side so everyone nearby sees
 *       somebody bouncing around trailing sherbet.</li>
 *   <li><b>The pop</b> — a soft fizz-out when it runs out, which is the punchline. An effect that
 *       simply stopped would leave the player wondering when it had.</li>
 * </ul>
 */
@NullMarked
@EventBusSubscriber(modid = WizardsAndBeastsMod.MODID)
public final class WhizzbeeHandler {

    private WhizzbeeHandler() {}

    /** Every jump while fizzing gets a little extra. */
    @SubscribeEvent
    public static void onJump(LivingEvent.LivingJumpEvent event) {
        LivingEntity jumper = event.getEntity();
        if (jumper.level().isClientSide() || !jumper.hasEffect(ModEffects.FIZZING)) {
            return;
        }
        Vec3 motion = jumper.getDeltaMovement();
        jumper.setDeltaMovement(motion.x, Whizzbee.whizz(motion.y), motion.z);
        // Non-living entities sync velocity on their own; a player needs telling, or the kick happens
        // server-side only and the client sees an ordinary jump.
        jumper.hurtMarked = true;

        jumper.level().playSound(null, jumper.getX(), jumper.getY(), jumper.getZ(),
                SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.35f, 1.9f);
        if (jumper.level() instanceof ServerLevel level) {
            level.sendParticles(ParticleTypes.END_ROD,
                    jumper.getX(), jumper.getY() + 0.1, jumper.getZ(), 8, 0.2, 0.05, 0.2, 0.04);
        }
    }

    /** The steady sputter around the feet. */
    @SubscribeEvent
    public static void onEntityTick(EntityTickEvent.Post event) {
        if (!(event.getEntity() instanceof LivingEntity entity)
                || entity.level().isClientSide()
                || !entity.hasEffect(ModEffects.FIZZING)) {
            return;
        }
        if (entity.tickCount % Whizzbee.FIZZ_INTERVAL != 0) {
            return;
        }
        ((ServerLevel) entity.level()).sendParticles(ParticleTypes.BUBBLE_POP,
                entity.getX(), entity.getY() + 0.05, entity.getZ(), 2, 0.28, 0.02, 0.28, 0.01);
    }

    /** Running out is the joke landing. */
    @SubscribeEvent
    public static void onEffectExpired(MobEffectEvent.Expired event) {
        if (event.getEffectInstance() == null
                || !event.getEffectInstance().getEffect().is(ModEffects.FIZZING)) {
            return;
        }
        LivingEntity entity = event.getEntity();
        if (entity.level().isClientSide()) {
            return;
        }
        entity.level().playSound(null, entity.getX(), entity.getY(), entity.getZ(),
                SoundEvents.BREWING_STAND_BREW, SoundSource.PLAYERS, 0.5f, 1.9f);
        if (entity.level() instanceof ServerLevel level) {
            level.sendParticles(ParticleTypes.BUBBLE_POP,
                    entity.getX(), entity.getY() + entity.getBbHeight() * 0.4, entity.getZ(),
                    24, 0.4, 0.5, 0.4, 0.08);
        }
    }
}
