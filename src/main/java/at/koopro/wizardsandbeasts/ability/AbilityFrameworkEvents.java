package at.koopro.wizardsandbeasts.ability;

import at.koopro.wizardsandbeasts.ability.select.AbilitySelectionState;
import at.koopro.wizardsandbeasts.network.ability.AbilityDefinitionsSyncS2CPayload;
import at.koopro.wizardsandbeasts.network.ability.AbilitySelectionSyncS2CPayload;
import at.koopro.wizardsandbeasts.network.apparition.ApparitionPointsSyncS2CPayload;
import at.koopro.wizardsandbeasts.registry.ModAttachments;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.OnDatapackSyncEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import org.jspecify.annotations.NullMarked;

/**
 * Server-side lifecycle for the ability framework, registered on {@code NeoForge.EVENT_BUS}:
 * <ul>
 *   <li>pushes the definition registry + per-player wheel state on datapack sync (login + {@code /reload});</li>
 *   <li>carries selection/pin/toggles across a clone, dropping cooldowns on death (§2.2);</li>
 *   <li>re-syncs wheel state after respawn.</li>
 * </ul>
 */
@NullMarked
public final class AbilityFrameworkEvents {

    private AbilityFrameworkEvents() {}

    @SubscribeEvent
    public static void onDatapackSync(OnDatapackSyncEvent event) {
        if (event.getPlayer() != null) {
            syncTo(event.getPlayer());
        } else {
            for (ServerPlayer player : event.getPlayerList().getPlayers()) {
                syncTo(player);
            }
        }
    }

    @SubscribeEvent
    public static void onClone(PlayerEvent.Clone event) {
        if (!(event.getEntity() instanceof ServerPlayer newPlayer)) {
            return;
        }
        AbilitySelectionState original = event.getOriginal().getData(ModAttachments.ABILITY_SELECTION.get());
        AbilitySelectionState carried = event.isWasDeath() ? original.withoutCooldowns() : original;
        newPlayer.setData(ModAttachments.ABILITY_SELECTION.get(), carried);
    }

    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            AbilitySelectionSyncS2CPayload.syncToPlayer(player);
            ApparitionPointsSyncS2CPayload.syncToPlayer(player);
        }
    }

    private static void syncTo(ServerPlayer player) {
        AbilityDefinitionsSyncS2CPayload.syncToPlayer(player);
        AbilitySelectionSyncS2CPayload.syncToPlayer(player);
        // The Apparition selector reads its list client-side, so it rides the same sync seam.
        ApparitionPointsSyncS2CPayload.syncToPlayer(player);
    }
}
