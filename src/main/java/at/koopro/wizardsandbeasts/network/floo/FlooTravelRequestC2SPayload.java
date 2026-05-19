package at.koopro.wizardsandbeasts.network.floo;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.floo.FlooTravelHandler;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jspecify.annotations.NonNull;

public record FlooTravelRequestC2SPayload(@NonNull String targetAddress) implements CustomPacketPayload {

    public static final Type<FlooTravelRequestC2SPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "floo_travel_request_c2s"));

    public static final StreamCodec<ByteBuf, FlooTravelRequestC2SPayload> STREAM_CODEC = StreamCodec.of(
            (buf, pkt) -> ByteBufCodecs.STRING_UTF8.encode(buf, pkt.targetAddress),
            buf -> new FlooTravelRequestC2SPayload(ByteBufCodecs.STRING_UTF8.decode(buf))
    );

    @Override
    public @NonNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handleServer(@NonNull FlooTravelRequestC2SPayload packet, @NonNull IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer player)) return;
            FlooTravelHandler.handleTravel(player, packet.targetAddress);
        });
    }
}
