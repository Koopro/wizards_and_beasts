package at.koopro.wizardsandbeasts.event.item;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.item.darkartefact.IHorcruxVessel;
import at.koopro.wizardsandbeasts.module.Module;
import at.koopro.wizardsandbeasts.module.ModuleManager;
import at.koopro.wizardsandbeasts.registry.ModAttachments;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * Passive dread aura shared by every Horcrux vessel (Slytherin's Locket, Hufflepuff's Cup,
 * Ravenclaw's Diadem, the Gaunt Ring, Riddle's Diary): while an intact-soul horcrux is carried,
 * it weighs on the bearer — slowly draining {@code MENTAL_STABILITY} and seeding
 * {@code DARK_CORRUPTION}, as they did to those who wore them in the books.
 * <p>
 * Only an intact soul fragment exerts the pull ({@link IHorcruxVessel#isSoulIntact}); a
 * destroyed horcrux is inert. Gated behind {@link Module#DARK_ARTS}.
 */
@EventBusSubscriber(modid = WizardsAndBeastsMod.MODID)
public final class HorcruxBearerTickHandler {

    private static final int APPLY_INTERVAL = 40;       // twice/second is plenty for a slow drain
    private static final float MENTAL_DRAIN_PER_APPLY = 0.5f;
    private static final float CORRUPTION_GAIN_PER_APPLY = 0.15f;
    private static final int WHISPER_INTERVAL = 1200;   // ~once per minute

    private HorcruxBearerTickHandler() {}

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!ModuleManager.isEnabled(Module.DARK_ARTS)) return;
        if (!(event.getEntity() instanceof ServerPlayer bearer) || !(bearer.level() instanceof ServerLevel level)) {
            return;
        }
        if (bearer.tickCount % APPLY_INTERVAL != 0) return;
        if (!isCarryingIntactLocket(bearer)) return;

        float mental = bearer.getData(ModAttachments.MENTAL_STABILITY.get());
        if (mental > 0f) {
            bearer.setData(ModAttachments.MENTAL_STABILITY.get(), Math.max(0f, mental - MENTAL_DRAIN_PER_APPLY));
        }
        float corruption = bearer.getData(ModAttachments.DARK_CORRUPTION.get());
        if (corruption < 100f) {
            bearer.setData(ModAttachments.DARK_CORRUPTION.get(), Math.min(100f, corruption + CORRUPTION_GAIN_PER_APPLY));
        }

        if (bearer.tickCount % WHISPER_INTERVAL == 0) {
            bearer.displayClientMessage(
                    Component.literal("The locket pulls cold against you.").withStyle(ChatFormatting.DARK_PURPLE), true);
            level.sendParticles(ParticleTypes.SMOKE, bearer.getX(), bearer.getY() + 1.0, bearer.getZ(),
                    4, 0.2, 0.2, 0.2, 0.005);
        }
    }

    private static boolean isCarryingIntactLocket(ServerPlayer player) {
        return isIntactLocket(player.getMainHandItem()) || isIntactLocket(player.getOffhandItem());
    }

    private static boolean isIntactLocket(ItemStack stack) {
        return stack.getItem() instanceof IHorcruxVessel vessel && vessel.isSoulIntact(stack);
    }
}
