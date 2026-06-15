package at.koopro.wizardsandbeasts.event.item;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.module.Module;
import at.koopro.wizardsandbeasts.module.ModuleManager;
import at.koopro.wizardsandbeasts.registry.ModDataComponents;
import at.koopro.wizardsandbeasts.registry.TrinketItemRegistry;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * Drives the Hand of Glory: while a player holds a <em>lit</em> Hand of Glory, the holder
 * receives night vision and every other player within {@link #BLIND_RADIUS} is blinded —
 * the canonical "gives light only to the one who holds it" behaviour.
 * <p>
 * Effects are short-lived and refreshed each {@link #REFRESH_INTERVAL} ticks so they end
 * promptly once the candle is snuffed, the item is stowed, or the holder dies.
 * Gated behind {@link Module#DARK_ARTS}.
 */
@EventBusSubscriber(modid = WizardsAndBeastsMod.MODID)
public final class HandOfGloryTickHandler {

    private static final int REFRESH_INTERVAL = 10;
    /** Slightly longer than the refresh interval so effects never visibly flicker. */
    private static final int EFFECT_DURATION = 30;
    private static final double BLIND_RADIUS = 16.0;

    private HandOfGloryTickHandler() {}

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!ModuleManager.isEnabled(Module.DARK_ARTS)) return;
        if (!(event.getEntity() instanceof ServerPlayer holder) || !(holder.level() instanceof ServerLevel level)) {
            return;
        }
        if (holder.tickCount % REFRESH_INTERVAL != 0) return;
        if (!isHoldingLitHand(holder)) return;

        holder.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, EFFECT_DURATION, 0, true, false, false));

        AABB area = holder.getBoundingBox().inflate(BLIND_RADIUS);
        for (ServerPlayer other : level.getEntitiesOfClass(ServerPlayer.class, area, p -> p != holder)) {
            other.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, EFFECT_DURATION, 0, true, false, true));
        }
    }

    private static boolean isHoldingLitHand(ServerPlayer player) {
        return isLitHand(player.getMainHandItem()) || isLitHand(player.getOffhandItem());
    }

    private static boolean isLitHand(ItemStack stack) {
        return stack.is(TrinketItemRegistry.HAND_OF_GLORY.get())
                && stack.getOrDefault(ModDataComponents.HAND_OF_GLORY_CANDLE_LIT.get(), false);
    }
}
