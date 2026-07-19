package at.koopro.wizardsandbeasts.network.apparition;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.client.apparition.state.ClientApparitionPointsState;
import at.koopro.wizardsandbeasts.network.PacketCodecUtils;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jspecify.annotations.NullMarked;

/**
 * Server → client "open the destination selector". Sent only after the server has confirmed the player may
 * actually Apparate, so the screen never opens for someone who would just be refused.
 */
@NullMarked
public record OpenApparitionSelectorS2CPayload() implements CustomPacketPayload {

    public static final Type<OpenApparitionSelectorS2CPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "open_apparition_selector"));

    public static final StreamCodec<ByteBuf, OpenApparitionSelectorS2CPayload> STREAM_CODEC =
            PacketCodecUtils.noPayloadCodec(OpenApparitionSelectorS2CPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(OpenApparitionSelectorS2CPayload payload, IPayloadContext context) {
        context.enqueueWork(ClientApparitionPointsState::openSelector);
    }
}
