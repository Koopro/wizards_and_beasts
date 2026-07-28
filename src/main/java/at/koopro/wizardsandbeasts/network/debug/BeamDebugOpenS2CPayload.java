package at.koopro.wizardsandbeasts.network.debug;
import at.koopro.wizardsandbeasts.network.ClientScreenHooksInvoker;
import at.koopro.wizardsandbeasts.network.PacketCodecUtils;

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
public record BeamDebugOpenS2CPayload() implements CustomPacketPayload {

    public static final Type<BeamDebugOpenS2CPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "beam_debug_open"));

    public static final StreamCodec<ByteBuf, BeamDebugOpenS2CPayload> STREAM_CODEC =
            PacketCodecUtils.noPayloadCodec(BeamDebugOpenS2CPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handleClient(BeamDebugOpenS2CPayload pkt, IPayloadContext ctx) {
        ctx.enqueueWork(BeamDebugOpenS2CPayload::openClientScreenSafe);
    }

    /** Opens the beam style editor. Kept behind the invoker so this class stays dist-safe. */
    private static void openClientScreenSafe() {
        ClientScreenHooksInvoker.invoke("openBeamStyleScreen");
    }

    public static void sendToPlayer(ServerPlayer player) {
        PacketDistributor.sendToPlayer(player, new BeamDebugOpenS2CPayload());
    }
}
