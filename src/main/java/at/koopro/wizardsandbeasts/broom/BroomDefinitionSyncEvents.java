package at.koopro.wizardsandbeasts.broom;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.network.broom.BroomDefinitionsSyncS2CPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.OnDatapackSyncEvent;
import org.jspecify.annotations.NullMarked;

/**
 * Pushes the broom definition table to clients on login and on every {@code /reload}.
 *
 * <p>{@code OnDatapackSyncEvent} is the seam for exactly this: it fires once per player on join with
 * that player set, and once with a null player after a reload, which is the two cases a client-side
 * mirror has to cover. Same shape as {@code AbilityFrameworkEvents} and the brew recipe sync.
 *
 * <p>Without it the client's registry is empty on a dedicated server — the loader is registered on
 * {@code AddServerReloadListenersEvent} — and the entity's synced {@code DEFINITION_ID} resolves to
 * nothing, so every broom falls back to the code default and draws the generic sheet regardless of
 * what it actually is. See {@link BroomDefinitionsSyncS2CPayload} for why that failure hid in
 * single-player.
 */
@EventBusSubscriber(modid = WizardsAndBeastsMod.MODID)
@NullMarked
public final class BroomDefinitionSyncEvents {

    private BroomDefinitionSyncEvents() {}

    @SubscribeEvent
    public static void onDatapackSync(OnDatapackSyncEvent event) {
        if (event.getPlayer() != null) {
            BroomDefinitionsSyncS2CPayload.syncToPlayer(event.getPlayer());
            return;
        }
        for (ServerPlayer player : event.getPlayerList().getPlayers()) {
            BroomDefinitionsSyncS2CPayload.syncToPlayer(player);
        }
    }
}
