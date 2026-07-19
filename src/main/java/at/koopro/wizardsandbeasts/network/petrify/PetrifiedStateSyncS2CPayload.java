package at.koopro.wizardsandbeasts.network.petrify;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.network.PacketCodecUtils;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.UUID;

/**
 * Server → Client: broadcasts a player's petrification state to every player tracking them (and to
 * the petrified player themselves) — everyone watching needs to see the stone statue, not just the
 * victim, so this uses the tracking-broadcast pattern ({@link at.koopro.wizardsandbeasts.network.form.FormSyncS2CPayload}),
 * not the single-player-push pattern used for private data like {@code PlayerStatsSyncPayload}.
 */
public record PetrifiedStateSyncS2CPayload(UUID playerUUID, boolean isPetrified) implements CustomPacketPayload {

    public static final Type<PetrifiedStateSyncS2CPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "petrified_state_sync"));

    public static final StreamCodec<ByteBuf, PetrifiedStateSyncS2CPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public PetrifiedStateSyncS2CPayload decode(ByteBuf buf) {
            UUID uuid = PacketCodecUtils.readUUID(buf);
            boolean petrified = buf.readBoolean();
            return new PetrifiedStateSyncS2CPayload(uuid, petrified);
        }

        @Override
        public void encode(ByteBuf buf, PetrifiedStateSyncS2CPayload pkt) {
            PacketCodecUtils.writeUUID(buf, pkt.playerUUID);
            buf.writeBoolean(pkt.isPetrified);
        }
    };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void syncToTracking(ServerPlayer player, boolean isPetrified) {
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(player,
                new PetrifiedStateSyncS2CPayload(player.getUUID(), isPetrified));
    }
}
