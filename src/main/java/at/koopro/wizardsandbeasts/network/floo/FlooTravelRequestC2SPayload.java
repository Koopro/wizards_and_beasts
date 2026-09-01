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

/**
 * A destination the player has committed to.
 *
 * <p>{@code spoken} is not cosmetic and is not derivable on the server: it is the difference between
 * an address the player pointed at and one they typed, and that is what decides whether a misfire is
 * a die roll or their own mispronunciation. Sending it from the client is safe because it can only
 * ever make travel <em>riskier</em> in the mode the client claims — a client that lies and says
 * "spoken" for a clicked address gets exact-match resolution and no die roll, which is a worse deal,
 * not a better one.
 */
public record FlooTravelRequestC2SPayload(@NonNull String targetAddress,
                                          boolean spoken) implements CustomPacketPayload {

    public static final Type<FlooTravelRequestC2SPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "floo_travel_request_c2s"));

    public static final StreamCodec<ByteBuf, FlooTravelRequestC2SPayload> STREAM_CODEC = StreamCodec.of(
            (buf, pkt) -> {
                ByteBufCodecs.STRING_UTF8.encode(buf, pkt.targetAddress);
                ByteBufCodecs.BOOL.encode(buf, pkt.spoken);
            },
            buf -> new FlooTravelRequestC2SPayload(
                    ByteBufCodecs.STRING_UTF8.decode(buf),
                    ByteBufCodecs.BOOL.decode(buf))
    );

    @Override
    public @NonNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handleServer(@NonNull FlooTravelRequestC2SPayload packet, @NonNull IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer player)) return;
            FlooTravelHandler.beginTravel(player, packet.targetAddress, packet.spoken);
        });
    }
}
