package at.koopro.wizardsandbeasts.network.handbook;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.handbook.HandbookChapter;
import at.koopro.wizardsandbeasts.handbook.HandbookChapterManager;
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
 * Server → client sync of the full handbook chapter list. Mirrors the Bestiary sync payload
 * pattern, but carries the chapter <em>definitions</em> (Bestiary syncs player progress, not entries).
 */
public record SyncHandbookPayload(List<HandbookChapter> chapters) implements CustomPacketPayload {
    public static final Type<SyncHandbookPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "sync_handbook"));

    public static final StreamCodec<ByteBuf, SyncHandbookPayload> STREAM_CODEC =
            ByteBufCodecs.fromCodec(HandbookChapter.CODEC.listOf())
                    .map(SyncHandbookPayload::new, SyncHandbookPayload::chapters);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /**
     * Fires on player join and on {@code /reload} (server datapack sync), pushing the current
     * chapter list. On {@code /reload} {@link OnDatapackSyncEvent#getPlayer()} is {@code null}, so
     * every player is re-synced.
     */
    @EventBusSubscriber(modid = WizardsAndBeastsMod.MODID)
    public static final class SyncOnDatapack {
        @SubscribeEvent
        public static void onDatapackSync(OnDatapackSyncEvent event) {
            SyncHandbookPayload payload = new SyncHandbookPayload(HandbookChapterManager.serverChapters());
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
