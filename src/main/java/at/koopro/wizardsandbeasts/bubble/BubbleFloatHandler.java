package at.koopro.wizardsandbeasts.bubble;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.effect.ModEffects;
import at.koopro.wizardsandbeasts.registry.ConsumableItemRegistry;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.living.MobEffectEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import org.jspecify.annotations.NullMarked;

/**
 * Everything that happens while a bubble is holding somebody up.
 *
 * <h2>Held jump, without a packet</h2>
 * {@code ServerPlayer.getLastClientInput().jump()} is the client's input as vanilla already
 * replicates it every tick. So "rises while the jump key is held" needs no keybind, no payload and
 * no client class — the server simply reads a flag it was being sent anyway.
 *
 * <h2>Three separate ends</h2>
 * A float can finish by running out, by being popped by a hit, or by the player dying. All three go
 * through {@link BubbleFloat#pop} so the sound, the burst and the forgotten anchor cannot drift
 * apart, and {@link MobEffectEvent.Expired} catches the ordinary case without a duration check of its
 * own.
 */
@NullMarked
@EventBusSubscriber(modid = WizardsAndBeastsMod.MODID)
public final class BubbleFloatHandler {

    /** Ticks between the little bubbles that trail a floater. */
    private static final int TRAIL_INTERVAL = 5;
    /** Ticks between bubbles on the face of somebody mid-chew. */
    private static final int CHEW_INTERVAL = 2;

    private BubbleFloatHandler() {}

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        blowBubble(player);

        if (!player.hasEffect(ModEffects.BUBBLE_FLOAT)) {
            return;
        }
        Double anchor = BubbleFloat.anchorOf(player);
        if (anchor == null) {
            // Effect present with no anchor: reloaded world, /effect give, a datapack. Anchor here
            // rather than refusing to lift, so the ability never silently does nothing.
            BubbleFloat.anchor(player);
            return;
        }

        if (player.getLastClientInput().jump()) {
            Vec3 motion = player.getDeltaMovement();
            double target = BubbleFloat.targetVelocity(player.getY(), anchor, motion.y);
            player.setDeltaMovement(motion.x,
                    Mth.lerp(BubbleFloat.RISE_RESPONSE, motion.y, target),
                    motion.z);
            // Cancels the fall that vanilla would otherwise accumulate while the player is aloft.
            player.resetFallDistance();
            player.hurtMarked = true;
        }

        if (player.tickCount % TRAIL_INTERVAL == 0) {
            ((ServerLevel) player.level()).sendParticles(ParticleTypes.BUBBLE,
                    player.getX(), player.getY() + 0.2, player.getZ(), 3, 0.25, 0.1, 0.25, 0.0);
        }
    }

    /**
     * The bubble growing on a chewer's face, which is what everybody else sees.
     *
     * <p>Particles rather than a model on the player's head. A render layer would look better and
     * would be a client-only cosmetic that costs a model, a texture, a layer registration and a
     * render-state hook; this is server-spawned so every nearby player sees it, and it grows with the
     * chew because the radius is derived from how far through the use the chewer is.
     */
    private static void blowBubble(ServerPlayer player) {
        if (!player.isUsingItem()
                || !player.getUseItem().is(ConsumableItemRegistry.DROOBLES_BEST_BLOWING_GUM.get())) {
            return;
        }
        if (player.tickCount % CHEW_INTERVAL != 0) {
            return;
        }
        int total = player.getUseItem().getUseDuration(player);
        float progress = total <= 0 ? 1.0f : 1.0f - (player.getUseItemRemainingTicks() / (float) total);
        double radius = 0.12 + 0.28 * progress;

        Vec3 mouth = player.getEyePosition().add(player.getLookAngle().scale(0.35));
        ((ServerLevel) player.level()).sendParticles(ParticleTypes.BUBBLE,
                mouth.x, mouth.y, mouth.z, 4, radius, radius, radius, 0.0);
    }

    /** A hit pops the bubble. Any hit — that is the price of the ride. */
    @SubscribeEvent
    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        LivingEntity floater = event.getEntity();
        if (floater.level().isClientSide() || !floater.hasEffect(ModEffects.BUBBLE_FLOAT)) {
            return;
        }
        // Removed before the damage resolves, so the fall the player is about to take is an ordinary
        // one rather than one cushioned by an effect that is on its way out anyway.
        floater.removeEffect(ModEffects.BUBBLE_FLOAT);
        BubbleFloat.pop(floater);
    }

    /** Running out pops it too. */
    @SubscribeEvent
    public static void onEffectExpired(MobEffectEvent.Expired event) {
        if (event.getEffectInstance() == null
                || !event.getEffectInstance().getEffect().is(ModEffects.BUBBLE_FLOAT)) {
            return;
        }
        if (!event.getEntity().level().isClientSide()) {
            BubbleFloat.pop(event.getEntity());
        }
    }

    /** So does being removed any other way — a milk bucket, a command, a death. */
    @SubscribeEvent
    public static void onEffectRemoved(MobEffectEvent.Remove event) {
        if (event.getEffectInstance() == null
                || !event.getEffectInstance().getEffect().is(ModEffects.BUBBLE_FLOAT)) {
            return;
        }
        if (!event.getEntity().level().isClientSide()) {
            BubbleFloat.release(event.getEntity());
        }
    }
}
