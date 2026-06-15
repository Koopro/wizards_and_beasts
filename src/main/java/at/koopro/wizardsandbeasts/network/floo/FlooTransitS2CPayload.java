package at.koopro.wizardsandbeasts.network.floo;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jspecify.annotations.NonNull;

/**
 * Sent to the travelling player only: triggers the client spin/whoosh transit overlay
 * (the dizzy "glimpse of passing grates" sensation) for {@code durationTicks}.
 */
public record FlooTransitS2CPayload(int durationTicks) implements CustomPacketPayload {

    public static final Type<FlooTransitS2CPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "floo_transit_s2c"));

    public static final StreamCodec<ByteBuf, FlooTransitS2CPayload> STREAM_CODEC = StreamCodec.of(
            (buf, pkt) -> ByteBufCodecs.VAR_INT.encode(buf, pkt.durationTicks),
            buf -> new FlooTransitS2CPayload(ByteBufCodecs.VAR_INT.decode(buf))
    );

    @Override
    public @NonNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void send(@NonNull ServerPlayer player, int durationTicks) {
        PacketDistributor.sendToPlayer(player, new FlooTransitS2CPayload(durationTicks));
    }
}
