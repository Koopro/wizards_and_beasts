package at.koopro.wizardsandbeasts.event.floo;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.floo.call.FlooCallService;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.ServerChatEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/** Drives live Floo calls: caller upkeep, chat bridging, and shutdown cleanup. */
@EventBusSubscriber(modid = WizardsAndBeastsMod.MODID)
public final class FlooCallEvents {
    private FlooCallEvents() {
    }

    @SubscribeEvent
    public static void onPlayerTickPost(PlayerTickEvent.Post event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            FlooCallService.tick(player);
        }
    }

    @SubscribeEvent
    public static void onServerChat(ServerChatEvent event) {
        FlooCallService.handleChat(event.getPlayer(), event.getRawText());
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        FlooCallService.clearAll();
    }
}
