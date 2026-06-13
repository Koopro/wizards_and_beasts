package at.koopro.wizardsandbeasts.network.form;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Server → Client: toggles the form debug overlay on the client.
 */
public record DebugOverlayToggleS2CPayload(boolean enabled) implements CustomPacketPayload {

    public static final Type<DebugOverlayToggleS2CPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "debug_overlay_toggle"));

    public static final StreamCodec<ByteBuf, DebugOverlayToggleS2CPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public DebugOverlayToggleS2CPayload decode(ByteBuf buf) {
            return new DebugOverlayToggleS2CPayload(buf.readBoolean());
        }

        @Override
        public void encode(ByteBuf buf, DebugOverlayToggleS2CPayload pkt) {
            buf.writeBoolean(pkt.enabled);
        }
    };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void sendToPlayer(ServerPlayer player, boolean enabled) {
        PacketDistributor.sendToPlayer(player, new DebugOverlayToggleS2CPayload(enabled));
    }
}
