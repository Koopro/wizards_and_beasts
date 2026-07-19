package at.koopro.wizardsandbeasts.module;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import org.jspecify.annotations.NullMarked;

/**
 * Brings the world's module state into the read cache when the server is up, and hands each joining player
 * the current snapshot.
 *
 * <p>The load on {@link ServerStartedEvent} matters: until it runs, {@link ModuleManager} is answering from
 * the build's shipped defaults, which may differ from what this particular world has stored.
 */
@NullMarked
@EventBusSubscriber(modid = WizardsAndBeastsMod.MODID)
public final class ModuleLifecycleEvents {

    private ModuleLifecycleEvents() {}

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        // Seeds a fresh world from config on first access, then populates the cache from world truth.
        ModuleStateService.refreshAndBroadcast(event.getServer());
    }

    @SubscribeEvent
    public static void onPlayerJoin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            ModuleStateService.syncTo(player);
        }
    }
}
