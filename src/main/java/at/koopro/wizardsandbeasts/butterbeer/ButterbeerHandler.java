package at.koopro.wizardsandbeasts.butterbeer;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.effect.ModEffects;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.NeutralMob;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import org.jspecify.annotations.NullMarked;

/**
 * What Warmth and Mellow actually do, once they are in you.
 *
 * <h2>Warmth</h2>
 * Freezing is unwound rather than merely held off: the frost counter is zeroed every tick, so a
 * player who drinks halfway through freezing to death thaws instead of being frozen in place at
 * whatever tick they drank. Freezing damage is cancelled outright, which covers the frame between
 * the counter being zeroed and vanilla applying the hit it had already decided on.
 *
 * <h2>Mellow</h2>
 * Only {@link NeutralMob}s are turned aside, and only while they have not been provoked. That is a
 * narrower rule than the mod's Camouflage — which hides you from everything — and the narrowness is
 * the point: a warm drink should make a wolf pack decide you are not worth it, and should do exactly
 * nothing about the zombie already running at you.
 */
@NullMarked
@EventBusSubscriber(modid = WizardsAndBeastsMod.MODID)
public final class ButterbeerHandler {

    /** Ticks between puffs of golden steam. Sparse — this is a glow, not a smoke machine. */
    private static final int STEAM_INTERVAL = 8;

    private ButterbeerHandler() {}

    @SubscribeEvent
    public static void onEntityTick(EntityTickEvent.Post event) {
        if (!(event.getEntity() instanceof LivingEntity entity)
                || entity.level().isClientSide()
                || !entity.hasEffect(ModEffects.WARMTH)) {
            return;
        }
        if (entity.getTicksFrozen() > 0) {
            entity.setTicksFrozen(0);
        }
        if (entity.tickCount % STEAM_INTERVAL == 0 && entity.level() instanceof ServerLevel level) {
            // Placed at chest height and drifting up on its own: sendParticles' speed argument is
            // what gives it the rise, so no per-particle motion has to be tracked.
            level.sendParticles(ParticleTypes.FALLING_HONEY,
                    entity.getX(), entity.getY() + entity.getBbHeight() * 0.6, entity.getZ(),
                    2, 0.25, 0.2, 0.25, 0.0);
            level.sendParticles(ParticleTypes.END_ROD,
                    entity.getX(), entity.getY() + entity.getBbHeight() * 0.5, entity.getZ(),
                    1, 0.2, 0.1, 0.2, 0.005);
        }
    }

    /** Warmth turns freezing damage away entirely. */
    @SubscribeEvent
    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        if (event.getSource().is(DamageTypeTags.IS_FREEZING)
                && event.getEntity().hasEffect(ModEffects.WARMTH)) {
            event.setCanceled(true);
        }
    }

    /**
     * A neutral thing will not pick a mellow drinker.
     *
     * <p>Checked against {@code getPersistentAngerTarget} so a mob that has already been hit keeps
     * coming — "unless attacked" is the whole rule, and a Mellow that pacified something you had
     * just shot would be a much stronger effect than a warm drink has any business being.
     */
    @SubscribeEvent
    public static void onChangeTarget(LivingChangeTargetEvent event) {
        if (!(event.getNewAboutToBeSetTarget() instanceof Player drinker)
                || !drinker.hasEffect(ModEffects.MELLOW)) {
            return;
        }
        if (!(event.getEntity() instanceof NeutralMob neutral)) {
            return;
        }
        if (drinker.getUUID().equals(neutral.getPersistentAngerTarget())) {
            return;
        }
        event.setCanceled(true);
    }
}
