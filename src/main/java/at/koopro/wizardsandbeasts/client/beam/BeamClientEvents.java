package at.koopro.wizardsandbeasts.client.beam;

import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;

/**
 * Keeps the beam package's per-entity caches from outliving the entities they key on — the
 * {@link WandTipTracker} anchors and the live beams in {@link BeamChannelClient}. Without this both
 * maps would hold stale entries for entity ids that a later session or a later entity can reuse.
 */
public final class BeamClientEvents {

    private BeamClientEvents() {}

    /** Whole client level is going away — drop every anchor and every beam. */
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        WandTipTracker.clear();
        BeamChannelClient.clear();
    }

    /**
     * One entity left the level. Drops its anchor, and its beam if it was casting — a caster that
     * dies or unloads never sends a channel-end packet, so without this the beam would hang.
     */
    public static void onEntityLeaveLevel(EntityLeaveLevelEvent event) {
        WandTipTracker.forget(event.getEntity().getId());
        BeamChannelClient.forget(event.getEntity().getId());
    }
}
