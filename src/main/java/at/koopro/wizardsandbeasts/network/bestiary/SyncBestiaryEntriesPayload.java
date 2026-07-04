package at.koopro.wizardsandbeasts.network.bestiary;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.bestiary.BestiaryEntry;
import at.koopro.wizardsandbeasts.bestiary.BestiaryEntryRegistry;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.OnDatapackSyncEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;

/**
 * Server → client sync of the full bestiary entry list, mirroring {@code SyncHandbookPayload}.
 * The client screen must not read the server-populated {@link BestiaryEntryRegistry} directly —
 * that only works in a shared single-player JVM; on a dedicated server the client-side registry
 * is empty. Clients store the synced list via {@link BestiaryEntryRegistry#setClientEntries}.
 */
public record SyncBestiaryEntriesPayload(List<BestiaryEntry> entries) implements CustomPacketPayload {
    public static final Type<SyncBestiaryEntriesPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "sync_bestiary_entries"));

    public static final StreamCodec<ByteBuf, SyncBestiaryEntriesPayload> STREAM_CODEC =
            ByteBufCodecs.fromCodec(BestiaryEntry.CODEC.listOf())
                    .map(SyncBestiaryEntriesPayload::new, SyncBestiaryEntriesPayload::entries);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /**
     * Fires on player join and on {@code /reload} (server datapack sync), pushing the current
     * entry list. On {@code /reload} {@link OnDatapackSyncEvent#getPlayer()} is {@code null}, so
     * every player is re-synced.
     */
    @EventBusSubscriber(modid = WizardsAndBeastsMod.MODID)
    public static final class SyncOnDatapack {
        @SubscribeEvent
        public static void onDatapackSync(OnDatapackSyncEvent event) {
            SyncBestiaryEntriesPayload payload = new SyncBestiaryEntriesPayload(List.copyOf(BestiaryEntryRegistry.getAll()));
            ServerPlayer joining = event.getPlayer();
            if (joining != null) {
                PacketDistributor.sendToPlayer(joining, payload);
            } else {
                for (ServerPlayer player : event.getPlayerList().getPlayers()) {
                    PacketDistributor.sendToPlayer(player, payload);
                }
            }
        }
    }
}
