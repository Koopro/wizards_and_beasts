package at.koopro.wizardsandbeasts.network.spell;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.client.spell.state.ClientSignatureSpellState;
import io.netty.buffer.ByteBuf;
import net.minecraft.client.Minecraft;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jspecify.annotations.NonNull;

public record CrucioIntentFeedbackS2CPayload(float intentMultiplier) implements CustomPacketPayload {
    public static final Type<CrucioIntentFeedbackS2CPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "crucio_intent_feedback"));

    public static final StreamCodec<ByteBuf, CrucioIntentFeedbackS2CPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.FLOAT, CrucioIntentFeedbackS2CPayload::intentMultiplier,
            CrucioIntentFeedbackS2CPayload::new);

    @Override
    public @NonNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handleClient(CrucioIntentFeedbackS2CPayload pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (Minecraft.getInstance().player != null) {
                ClientSignatureSpellState.setLastIntentMultiplier(pkt.intentMultiplier);
                ClientSignatureSpellState.triggerImperioScreenShake();
            }
        });
    }
}
