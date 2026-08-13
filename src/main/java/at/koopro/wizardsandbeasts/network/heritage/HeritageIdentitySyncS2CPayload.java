package at.koopro.wizardsandbeasts.network.heritage;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.heritage.Heritage;
import at.koopro.wizardsandbeasts.heritage.HeritageVariant;
import at.koopro.wizardsandbeasts.heritage.data.PlayerHeritageData;
import at.koopro.wizardsandbeasts.network.PacketCodecUtils;
import at.koopro.wizardsandbeasts.registry.ModAttachments;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.UUID;

/**
 * Server → client: which heritage and variant a player is, broadcast to everyone tracking them.
 *
 * <p><b>Why this exists.</b> {@code HeritageDataSyncS2CPayload} sends the same two facts, but only
 * ever to the player they belong to, and {@code ClientHeritageDataState} stores them in a
 * <em>single</em> {@code PlayerHeritageData} instance. So before this payload the client knew its own
 * heritage and nobody else's — which is fine for a HUD and useless for rendering, because the whole
 * point of a visible heritage is that other people see it.
 *
 * <p>Deliberately narrow: identity only, no profession points, no flags, no transformation state.
 * Everything here is already public knowledge the moment the player is visible, so broadcasting it
 * leaks nothing that looking at them would not.
 *
 * <p>Mirrors {@code FormSyncS2CPayload} — same {@code syncToTracking} / {@code syncTo} pair, driven
 * from the same lifecycle quartet in {@code FormLifecycleHandler}.
 *
 * @param playerUUID the player this describes
 * @param heritageId {@link Heritage#getId()}, or empty for a player who has not chosen yet
 * @param variantId  {@link HeritageVariant#getId()}, or empty
 */
public record HeritageIdentitySyncS2CPayload(
        UUID playerUUID,
        String heritageId,
        String variantId
) implements CustomPacketPayload {

    public static final Type<HeritageIdentitySyncS2CPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "heritage_identity_sync"));

    public static final StreamCodec<ByteBuf, HeritageIdentitySyncS2CPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public HeritageIdentitySyncS2CPayload decode(ByteBuf buf) {
            UUID uuid = PacketCodecUtils.readUUID(buf);
            String heritageId = PacketCodecUtils.readString(buf);
            String variantId = PacketCodecUtils.readString(buf);
            return new HeritageIdentitySyncS2CPayload(uuid, heritageId, variantId);
        }

        @Override
        public void encode(ByteBuf buf, HeritageIdentitySyncS2CPayload pkt) {
            PacketCodecUtils.writeUUID(buf, pkt.playerUUID);
            PacketCodecUtils.writeString(buf, pkt.heritageId);
            PacketCodecUtils.writeString(buf, pkt.variantId);
        }
    };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /** Announces {@code player}'s identity to every tracker and to the player themselves. */
    public static void syncToTracking(ServerPlayer player) {
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(player, of(player));
    }

    /**
     * Tells one observer about one target. Used when an observer first learns about the target; a
     * broadcast would be correct but re-tells every other tracker something they already know.
     */
    public static void syncTo(ServerPlayer observer, ServerPlayer target) {
        PacketDistributor.sendToPlayer(observer, of(target));
    }

    private static HeritageIdentitySyncS2CPayload of(ServerPlayer player) {
        PlayerHeritageData data = player.getData(ModAttachments.HERITAGE_DATA.get());
        Heritage heritage = data.getSelectedHeritage();
        HeritageVariant variant = data.getSelectedHeritageVariant();
        return new HeritageIdentitySyncS2CPayload(
                player.getUUID(),
                heritage == null ? "" : heritage.getId(),
                variant == null ? "" : variant.getId());
    }
}
