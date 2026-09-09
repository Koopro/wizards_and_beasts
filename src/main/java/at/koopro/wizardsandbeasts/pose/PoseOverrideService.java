package at.koopro.wizardsandbeasts.pose;

import at.koopro.wizardsandbeasts.network.pose.PoseOverrideSyncS2CPayload;
import at.koopro.wizardsandbeasts.registry.ModAttachments;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * The one place a {@code PoseOverride} is written and broadcast.
 *
 * <p>Server-side. Everything that changes an override goes through here so the write and the sync
 * cannot drift apart — the failure that causes is a pose the setter can see and nobody else can,
 * which looks like a rendering bug and is actually a missing packet.
 */
@NullMarked
public final class PoseOverrideService {

    private PoseOverrideService() {}

    public static PoseOverride get(ServerPlayer player) {
        return player.getData(ModAttachments.POSE_OVERRIDE.get());
    }

    /** Forces a state and holds it until explicitly cleared. */
    public static void force(ServerPlayer player, FlightPoseState state) {
        set(player, PoseOverride.forced(state));
    }

    /**
     * Records a state derived from movement.
     *
     * <p>No-ops when a manual override is in force: {@code auto} re-derives every tick, and letting
     * it overwrite a command-forced state would make the forced modes untestable — the thing they
     * exist for is holding still while you look at them.
     */
    public static void derive(ServerPlayer player, @Nullable FlightPoseState state) {
        PoseOverride current = get(player);
        if (current.manual()) {
            return;
        }
        set(player, state == null ? PoseOverride.NONE : PoseOverride.derived(state));
    }

    public static void clear(ServerPlayer player) {
        set(player, PoseOverride.NONE);
    }

    /** Writes and broadcasts. Skips the packet when nothing actually changed. */
    public static void set(ServerPlayer player, PoseOverride override) {
        if (get(player).equals(override)) {
            return;
        }
        player.setData(ModAttachments.POSE_OVERRIDE.get(), override);
        // The attitude decides the collision box (FlightHitbox), and a box is only recomputed when
        // something asks for it. Without this the server keeps the standing box while the model
        // lies flat, which is the whole defect this is here to prevent.
        player.refreshDimensions();
        sync(player);
    }

    /**
     * Broadcasts a player's override to everyone tracking them, and to themselves.
     *
     * <p>{@code sendToPlayersTrackingEntity} excludes the entity's own player, so the self-send is
     * separate — without it the one client guaranteed to care would be the only one not told.
     */
    public static void sync(ServerPlayer player) {
        PoseOverrideSyncS2CPayload payload =
                new PoseOverrideSyncS2CPayload(player.getUUID(), get(player));
        PacketDistributor.sendToPlayersTrackingEntity(player, payload);
        PacketDistributor.sendToPlayer(player, payload);
    }
}
