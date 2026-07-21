package at.koopro.wizardsandbeasts.event.floo;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.floo.call.FlooCallService;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.ServerChatEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
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
    public static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        // Without this, a caller who disconnects mid-call leaves a live session that the per-tick
        // countdown never drains (it only runs while the player is online). FlooTravelHandler then
        // rejects that player from all future Floo travel ("You are mid-Floo-call...") — a permanent
        // soft-lock cleared only by starting another call. End the stuck call on logout.
        if (event.getEntity() instanceof ServerPlayer player
                && player.level() instanceof ServerLevel level) {
            MinecraftServer server = level.getServer();
            FlooCallService.endCall(server, player.getUUID(), null);
        }
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        FlooCallService.clearAll();
    }
}
