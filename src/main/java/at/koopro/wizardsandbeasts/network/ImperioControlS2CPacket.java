package at.koopro.wizardsandbeasts.network;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.client.state.ClientSignatureSpellState;
import io.netty.buffer.ByteBuf;
import net.minecraft.client.Minecraft;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jspecify.annotations.NonNull;

import java.util.UUID;

public record ImperioControlS2CPacket(boolean isControlled, UUID controllerUUID) implements CustomPacketPayload {
    public static final Type<ImperioControlS2CPacket> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "imperio_control"));

    public static final StreamCodec<ByteBuf, ImperioControlS2CPacket> STREAM_CODEC = StreamCodec.composite(
            net.minecraft.network.codec.ByteBufCodecs.BOOL, ImperioControlS2CPacket::isControlled,
            UUIDUtil.STREAM_CODEC, ImperioControlS2CPacket::controllerUUID,
            ImperioControlS2CPacket::new);

    @Override
    public @NonNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handleClient(ImperioControlS2CPacket pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (Minecraft.getInstance().player != null) {
                ClientSignatureSpellState.setImperioControl(pkt.isControlled, pkt.controllerUUID);
            }
        });
    }
}
