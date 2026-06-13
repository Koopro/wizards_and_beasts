package at.koopro.wizardsandbeasts.network.spell;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
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
}
