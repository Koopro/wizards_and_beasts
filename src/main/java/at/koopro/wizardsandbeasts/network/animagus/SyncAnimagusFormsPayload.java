package at.koopro.wizardsandbeasts.network.animagus;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.animagus.AnimagusFormDefinition;
import at.koopro.wizardsandbeasts.animagus.AnimagusFormRegistry;
import com.mojang.serialization.Codec;
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
import org.jspecify.annotations.NullMarked;

import java.util.Map;

/**
 * Server → client sync of the Animagus form registry, mirroring {@code SyncBestiaryEntriesPayload}.
 * <p>
 * The client cannot read the server-populated registry: that only works in a shared single-player
 * JVM, and on a dedicated server the client-side map is empty. The renderer needs the rig, texture
 * and animation map, and the flight predictor needs the physics, so the whole definition is sent
 * rather than a digest.
 */
@NullMarked
public record SyncAnimagusFormsPayload(Map<Identifier, AnimagusFormDefinition> forms)
        implements CustomPacketPayload {

    public static final Type<SyncAnimagusFormsPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "sync_animagus_forms"));

    public static final StreamCodec<ByteBuf, SyncAnimagusFormsPayload> STREAM_CODEC =
            ByteBufCodecs.fromCodec(Codec.unboundedMap(Identifier.CODEC, AnimagusFormDefinition.CODEC))
                    .map(SyncAnimagusFormsPayload::new, SyncAnimagusFormsPayload::forms);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /** Snapshots the current server-side registry. */
    public static SyncAnimagusFormsPayload current() {
        Map<Identifier, AnimagusFormDefinition> snapshot = new java.util.HashMap<>();
        for (Identifier id : AnimagusFormRegistry.ids()) {
            AnimagusFormDefinition def = AnimagusFormRegistry.get(id);
            if (def != null) {
                snapshot.put(id, def);
            }
        }
        return new SyncAnimagusFormsPayload(snapshot);
    }

    /**
     * Fires on join and on {@code /reload}. {@link OnDatapackSyncEvent#getPlayer()} is null for a
     * reload, in which case every player is re-synced.
     */
    @EventBusSubscriber(modid = WizardsAndBeastsMod.MODID)
    public static final class SyncOnDatapack {
        @SubscribeEvent
        public static void onDatapackSync(OnDatapackSyncEvent event) {
            SyncAnimagusFormsPayload payload = current();
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
