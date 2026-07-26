package at.koopro.wizardsandbeasts.client.beam;

import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;

/**
 * Keeps {@link WandTipTracker}'s cache from outliving the entities it keys on. Without this the
 * map would hold stale anchors for entity ids that a later session or a later entity can reuse.
 */
public final class BeamClientEvents {

    private BeamClientEvents() {}

    /** Whole client level is going away — drop every anchor. */
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        WandTipTracker.clear();
    }

    /** One entity left the level — drop just its anchor. */
    public static void onEntityLeaveLevel(EntityLeaveLevelEvent event) {
        WandTipTracker.forget(event.getEntity().getId());
    }
}
