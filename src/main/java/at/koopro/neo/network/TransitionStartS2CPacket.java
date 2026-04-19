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
 * Server → Client: signals a transformation transition is starting.
 * Sent to the target player and all tracking players.
 */
public record TransitionStartS2CPacket(
        UUID playerUUID,
        String fromFormId,
        String toFormId,
        int durationTicks,
        int screenEffectOrdinal
) implements CustomPacketPayload {

    public static final Type<TransitionStartS2CPacket> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(Neo.MODID, "transition_start"));

    public static final StreamCodec<ByteBuf, TransitionStartS2CPacket> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public TransitionStartS2CPacket decode(ByteBuf buf) {
            UUID uuid = PacketCodecUtils.readUUID(buf);
            String from = PacketCodecUtils.readString(buf);
            String to = PacketCodecUtils.readString(buf);
            int duration = buf.readInt();
            int effect = buf.readInt();
            return new TransitionStartS2CPacket(uuid, from, to, duration, effect);
        }

        @Override
        public void encode(ByteBuf buf, TransitionStartS2CPacket pkt) {
            PacketCodecUtils.writeUUID(buf, pkt.playerUUID);
            PacketCodecUtils.writeString(buf, pkt.fromFormId);
            PacketCodecUtils.writeString(buf, pkt.toFormId);
            buf.writeInt(pkt.durationTicks);
            buf.writeInt(pkt.screenEffectOrdinal);
        }
    };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handleClient(TransitionStartS2CPacket pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            ClientTransitionTracker.startTransition(
                    pkt.playerUUID, pkt.fromFormId, pkt.toFormId,
                    pkt.durationTicks, pkt.screenEffectOrdinal);
        });
    }

    public static void sendToTracking(ServerPlayer player, String fromFormId, String toFormId,
                                       int durationTicks, int screenEffectOrdinal) {
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(player,
                new TransitionStartS2CPacket(player.getUUID(), fromFormId, toFormId,
                        durationTicks, screenEffectOrdinal));
    }
}
