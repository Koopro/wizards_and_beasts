package at.koopro.wizardsandbeasts.network;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import io.netty.buffer.ByteBuf;
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
            Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "beam_debug_open"));

    public static final StreamCodec<ByteBuf, BeamDebugOpenS2CPacket> STREAM_CODEC =
            PacketCodecUtils.noPayloadCodec(BeamDebugOpenS2CPacket::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handleClient(BeamDebugOpenS2CPacket pkt, IPayloadContext ctx) {
        ctx.enqueueWork(BeamDebugOpenS2CPacket::openClientScreenSafe);
    }

    private static void openClientScreenSafe() {
        ClientScreenHooksInvoker.invoke("openBeamDebugScreen");
    }

    public static void sendToPlayer(ServerPlayer player) {
        PacketDistributor.sendToPlayer(player, new BeamDebugOpenS2CPacket());
    }
}
