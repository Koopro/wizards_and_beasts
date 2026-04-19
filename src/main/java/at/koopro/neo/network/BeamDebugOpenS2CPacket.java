package at.koopro.neo.network;

import at.koopro.neo.Neo;
import at.koopro.neo.client.gui.BeamDebugScreen;
import io.netty.buffer.ByteBuf;
import net.minecraft.client.Minecraft;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Server -> Client: opens the beam debug editor screen.
 */
public record BeamDebugOpenS2CPacket() implements CustomPacketPayload {

    public static final Type<BeamDebugOpenS2CPacket> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(Neo.MODID, "beam_debug_open"));

    public static final StreamCodec<ByteBuf, BeamDebugOpenS2CPacket> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public BeamDebugOpenS2CPacket decode(ByteBuf buf) {
            return new BeamDebugOpenS2CPacket();
        }

        @Override
        public void encode(ByteBuf buf, BeamDebugOpenS2CPacket pkt) {
            // no payload
        }
    };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handleClient(BeamDebugOpenS2CPacket pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            Minecraft.getInstance().setScreen(new BeamDebugScreen());
        });
    }

    public static void sendToPlayer(ServerPlayer player) {
        PacketDistributor.sendToPlayer(player, new BeamDebugOpenS2CPacket());
    }
}
