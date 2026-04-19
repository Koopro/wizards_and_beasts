package at.koopro.neo.network;

import at.koopro.neo.Neo;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.UUID;

/**
 * Server → Client: signals a transformation transition has completed.
 */
public record TransitionEndS2CPacket(UUID playerUUID, String newFormId) implements CustomPacketPayload {

    public static final Type<TransitionEndS2CPacket> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(Neo.MODID, "transition_end"));

    public static final StreamCodec<ByteBuf, TransitionEndS2CPacket> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public TransitionEndS2CPacket decode(ByteBuf buf) {
            UUID uuid = PacketCodecUtils.readUUID(buf);
            String formId = PacketCodecUtils.readString(buf);
            return new TransitionEndS2CPacket(uuid, formId);
        }

        @Override
        public void encode(ByteBuf buf, TransitionEndS2CPacket pkt) {
            PacketCodecUtils.writeUUID(buf, pkt.playerUUID);
            PacketCodecUtils.writeString(buf, pkt.newFormId);
        }
    };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handleClient(TransitionEndS2CPacket pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            ClientTransitionTracker.endTransition(pkt.playerUUID);
        });
    }

    public static void sendToTracking(ServerPlayer player, String newFormId) {
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(player,
                new TransitionEndS2CPacket(player.getUUID(), newFormId));
    }
}
