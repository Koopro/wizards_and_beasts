package at.koopro.wizardsandbeasts.network;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.client.state.ClientFormDataState;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Server → Client: toggles the form debug overlay on the client.
 */
public record DebugOverlayToggleS2CPacket(boolean enabled) implements CustomPacketPayload {

    public static final Type<DebugOverlayToggleS2CPacket> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "debug_overlay_toggle"));

    public static final StreamCodec<ByteBuf, DebugOverlayToggleS2CPacket> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public DebugOverlayToggleS2CPacket decode(ByteBuf buf) {
            return new DebugOverlayToggleS2CPacket(buf.readBoolean());
        }

        @Override
        public void encode(ByteBuf buf, DebugOverlayToggleS2CPacket pkt) {
            buf.writeBoolean(pkt.enabled);
        }
    };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handleClient(DebugOverlayToggleS2CPacket pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            ClientFormDataState.setDebugOverlay(pkt.enabled);
        });
    }

    public static void sendToPlayer(ServerPlayer player, boolean enabled) {
        PacketDistributor.sendToPlayer(player, new DebugOverlayToggleS2CPacket(enabled));
    }
}
