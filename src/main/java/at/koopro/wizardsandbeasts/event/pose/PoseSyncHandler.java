package at.koopro.wizardsandbeasts.event.pose;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.pose.PoseOverrideService;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import org.jspecify.annotations.NullMarked;

/**
 * Keeps every client's view of a player's pose override current.
 *
 * <p>Three moments matter, and each fails differently if missed:
 *
 * <ul>
 *   <li><b>Start tracking</b> — a player walking into render distance of a posed player has never
 *       received the packet. Without this they see the default pose while everyone else sees the
 *       override, which reads as a desync bug rather than a missing send.</li>
 *   <li><b>Respawn</b> — a fresh {@code ServerPlayer} is constructed, so the client's copy refers to
 *       an entity that no longer exists.</li>
 *   <li><b>Dimension change</b> — same, plus the tracking set is rebuilt from scratch on the far
 *       side.</li>
 * </ul>
 *
 * <p>The override itself is deliberately not {@code copyOnDeath} (see {@code ModAttachments}), so
 * the respawn send is usually broadcasting a cleared state — which is exactly the point: it tells
 * every client to stop drawing the pose the player died in.
 */
@NullMarked
@EventBusSubscriber(modid = WizardsAndBeastsMod.MODID)
public final class PoseSyncHandler {

    private PoseSyncHandler() {}

    @SubscribeEvent
    public static void onStartTracking(PlayerEvent.StartTracking event) {
        if (event.getTarget() instanceof ServerPlayer tracked) {
            PoseOverrideService.sync(tracked);
        }
    }

    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            PoseOverrideService.sync(player);
        }
    }

    @SubscribeEvent
    public static void onChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            PoseOverrideService.sync(player);
        }
    }

    @SubscribeEvent
    public static void onLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            PoseOverrideService.sync(player);
        }
    }
}
