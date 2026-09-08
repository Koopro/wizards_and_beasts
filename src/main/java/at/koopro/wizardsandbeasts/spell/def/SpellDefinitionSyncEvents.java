package at.koopro.wizardsandbeasts.spell.def;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.network.spell.SpellDefinitionsSyncS2CPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.OnDatapackSyncEvent;
import org.jspecify.annotations.NullMarked;

/**
 * Pushes the datapack spell table to clients on login and on every {@code /reload}.
 *
 * <p>{@code OnDatapackSyncEvent} is the seam for exactly this: it fires once per player on join with
 * that player set, and once with a null player after a reload, which is the two cases a client-side
 * mirror has to cover. Same shape as {@code BroomDefinitionSyncEvents} and the brew recipe sync.
 *
 * <p>See {@link SpellDefinitionsSyncS2CPayload} for what was broken without it, and for why the
 * failure was invisible in single-player.
 */
@EventBusSubscriber(modid = WizardsAndBeastsMod.MODID)
@NullMarked
public final class SpellDefinitionSyncEvents {

    private SpellDefinitionSyncEvents() {}

    @SubscribeEvent
    public static void onDatapackSync(OnDatapackSyncEvent event) {
        if (event.getPlayer() != null) {
            SpellDefinitionsSyncS2CPayload.syncToPlayer(event.getPlayer());
            return;
        }
        for (ServerPlayer player : event.getPlayerList().getPlayers()) {
            SpellDefinitionsSyncS2CPayload.syncToPlayer(player);
        }
    }
}
