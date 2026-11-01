package at.koopro.wizardsandbeasts.module;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.TagsUpdatedEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import org.jspecify.annotations.NullMarked;

/**
 * Brings the world's module state into the read cache before any chunk generates, tells clients once the server
 * is up, and hands each joining player the current snapshot.
 *
 * <p>The load on the overworld's {@link LevelEvent.Load} matters: until it runs, {@link ModuleManager} is answering
 * from the build's shipped defaults, or from the last world this game had open, either of which may differ from what
 * this world has stored. Vanilla fires it before the spawn search and the first chunks, which is why structure
 * gates can trust the cache. {@link ServerStartedEvent} is too late for them.
 */
@NullMarked
@EventBusSubscriber(modid = WizardsAndBeastsMod.MODID)
public final class ModuleLifecycleEvents {

    private ModuleLifecycleEvents() {}

    @SubscribeEvent
    public static void onLevelLoad(LevelEvent.Load event) {
        if (event.getLevel() instanceof ServerLevel level && level.dimension() == Level.OVERWORLD) {
            ModuleStateService.loadBeforeGeneration(level.getServer());
        }
    }

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

    /**
     * Rebuilds the content index whenever tags are (re)bound — datapack load, {@code /reload}, and the
     * client receiving the tag sync. Both causes are handled rather than only the server one: on a
     * dedicated server the client never sees {@code SERVER_DATA_LOAD}, and the client needs the index for
     * creative tabs.
     */
    @SubscribeEvent
    public static void onTagsUpdated(TagsUpdatedEvent event) {
        ModuleContentIndex.rebuild(event.getLookupProvider());
    }
}
