package at.koopro.wizardsandbeasts.creature;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.network.PayloadBroadcast;
import at.koopro.wizardsandbeasts.network.creature.CreatureDefinitionsSyncS2CPayload;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.OnDatapackSyncEvent;
import org.jspecify.annotations.NullMarked;

/**
 * Pushes the creature definition table to clients on login and after every {@code /reload}.
 *
 * <p>{@code OnDatapackSyncEvent} fires once for a joining player (before its chunks, so before any creature reaches
 * that client) and once with no player after a reload. See {@link CreatureDefinitionsSyncS2CPayload} for the bug
 * this closes. Same shape as {@code BroomDefinitionSyncEvents}.
 */
@EventBusSubscriber(modid = WizardsAndBeastsMod.MODID)
@NullMarked
public final class CreatureDefinitionSyncEvents {

    private CreatureDefinitionSyncEvents() {}

    @SubscribeEvent
    public static void onDatapackSync(OnDatapackSyncEvent event) {
        PayloadBroadcast.toSyncTarget(event, CreatureDefinitionsSyncS2CPayload::syncToPlayer);
    }
}
