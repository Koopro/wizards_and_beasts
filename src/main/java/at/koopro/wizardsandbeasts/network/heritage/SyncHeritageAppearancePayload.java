package at.koopro.wizardsandbeasts.network.heritage;

import at.koopro.wizardsandbeasts.network.PayloadBroadcast;
import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.heritage.appearance.HeritageAppearance;
import at.koopro.wizardsandbeasts.heritage.appearance.HeritageAppearanceRegistry;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.OnDatapackSyncEvent;

import java.util.List;

/**
 * Server → client sync of the full heritage appearance list, mirroring
 * {@code SyncBestiaryEntriesPayload}.
 *
 * <p>The render path must not read the server-populated {@link HeritageAppearanceRegistry} directly —
 * that only works in a shared single-player JVM, and on a dedicated server the client-side map is
 * empty. Clients store the synced list via {@link HeritageAppearanceRegistry#setClientEntries}.
 */
public record SyncHeritageAppearancePayload(List<HeritageAppearance> entries) implements CustomPacketPayload {

    public static final Type<SyncHeritageAppearancePayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "sync_heritage_appearance"));

    public static final StreamCodec<ByteBuf, SyncHeritageAppearancePayload> STREAM_CODEC =
            ByteBufCodecs.fromCodec(HeritageAppearance.CODEC.listOf())
                    .map(SyncHeritageAppearancePayload::new, SyncHeritageAppearancePayload::entries);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /**
     * Fires on player join and on {@code /reload}. On {@code /reload}
     * {@link OnDatapackSyncEvent#getPlayer()} is {@code null}, so every player is re-synced — which is
     * what makes the "edit a JSON, {@code /reload}, see it in-game" verification step work.
     */
    @EventBusSubscriber(modid = WizardsAndBeastsMod.MODID)
    public static final class SyncOnDatapack {
        @SubscribeEvent
        public static void onDatapackSync(OnDatapackSyncEvent event) {
            SyncHeritageAppearancePayload payload =
                    new SyncHeritageAppearancePayload(List.copyOf(HeritageAppearanceRegistry.getAll()));
            PayloadBroadcast.toSyncTarget(event, payload);
        }
    }
}
