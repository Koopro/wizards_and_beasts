package at.koopro.wizardsandbeasts.network;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.client.state.ClientSignatureSpellState;
import io.netty.buffer.ByteBuf;
import net.minecraft.client.Minecraft;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jspecify.annotations.NonNull;

public record CrucioIntentFeedbackS2CPacket(float intentMultiplier) implements CustomPacketPayload {
    public static final Type<CrucioIntentFeedbackS2CPacket> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "crucio_intent_feedback"));

    public static final StreamCodec<ByteBuf, CrucioIntentFeedbackS2CPacket> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.FLOAT, CrucioIntentFeedbackS2CPacket::intentMultiplier,
            CrucioIntentFeedbackS2CPacket::new);

    @Override
    public @NonNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handleClient(CrucioIntentFeedbackS2CPacket pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (Minecraft.getInstance().player != null) {
                ClientSignatureSpellState.setLastIntentMultiplier(pkt.intentMultiplier);
                ClientSignatureSpellState.triggerImperioScreenShake();
            }
        });
    }
}
