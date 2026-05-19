package at.koopro.wizardsandbeasts.network.spell;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.client.spell.state.ClientSignatureSpellState;
import io.netty.buffer.ByteBuf;
import net.minecraft.client.Minecraft;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jspecify.annotations.NonNull;

import java.util.UUID;

public record ImperioControlS2CPayload(boolean isControlled, UUID controllerUUID) implements CustomPacketPayload {
    public static final Type<ImperioControlS2CPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "imperio_control"));

    public static final StreamCodec<ByteBuf, ImperioControlS2CPayload> STREAM_CODEC = StreamCodec.composite(
            net.minecraft.network.codec.ByteBufCodecs.BOOL, ImperioControlS2CPayload::isControlled,
            UUIDUtil.STREAM_CODEC, ImperioControlS2CPayload::controllerUUID,
            ImperioControlS2CPayload::new);

    @Override
    public @NonNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handleClient(ImperioControlS2CPayload pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (Minecraft.getInstance().player != null) {
                ClientSignatureSpellState.setImperioControl(pkt.isControlled, pkt.controllerUUID);
            }
        });
    }
}
