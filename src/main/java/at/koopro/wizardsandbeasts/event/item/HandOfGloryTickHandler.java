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
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * Drives the Hand of Glory: "Insert a candle and it gives light only to the holder" (<i>Chamber of Secrets</i>
 * ch. 4). While a player holds a <em>lit</em> Hand, the holder sees in the dark — night vision, and any darkness laid
 * on them (Peruvian Instant Darkness Powder's blindness, a Warden's darkness) is lifted, which is how Draco leads the
 * Death Eaters through the powder in <i>Half-Blood Prince</i>. Nobody else is affected: the light is simply not
 * theirs to see. It used to blind every other player within sixteen blocks, which canon never says and which made a
 * thief's candle an area weapon (documentation/CANON_AUDIT.md C-7).
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

    private HandOfGloryTickHandler() {}

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!ModuleManager.isEnabled(Module.DARK_ARTS)) return;
        if (!(event.getEntity() instanceof ServerPlayer holder) || !(holder.level() instanceof ServerLevel)) {
            return;
        }
        if (holder.tickCount % REFRESH_INTERVAL != 0) return;
        if (!isHoldingLitHand(holder)) return;

        holder.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, EFFECT_DURATION, 0, true, false, false));
        holder.removeEffect(MobEffects.BLINDNESS);
        holder.removeEffect(MobEffects.DARKNESS);
    }

    private static boolean isHoldingLitHand(ServerPlayer player) {
        return isLitHand(player.getMainHandItem()) || isLitHand(player.getOffhandItem());
    }

    private static boolean isLitHand(ItemStack stack) {
        return stack.is(TrinketItemRegistry.HAND_OF_GLORY.get())
                && stack.getOrDefault(ModDataComponents.HAND_OF_GLORY_CANDLE_LIT.get(), false);
    }
}
