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
 * Sent to the travelling player only: the two screen effects of a hop, in the order they happen.
 *
 * <p>{@code durationTicks} is the dizzy spin of the journey — the glimpse of passing grates.
 * {@code sootTicks} is what is left on the lens when it stops: a fading grey vignette, because a
 * traveller arrives filthy and the world should look like it for a moment afterwards.
 *
 * <p>One payload rather than two, because they are one event. A soot packet that could arrive
 * without its spin, or arrive first, would be a way for the effects to desynchronise for the sake of
 * separating things that never happen apart.
 */
public record FlooTransitS2CPayload(int durationTicks, int sootTicks) implements CustomPacketPayload {

    public static final Type<FlooTransitS2CPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "floo_transit_s2c"));

    public static final StreamCodec<ByteBuf, FlooTransitS2CPayload> STREAM_CODEC = StreamCodec.of(
            (buf, pkt) -> {
                ByteBufCodecs.VAR_INT.encode(buf, pkt.durationTicks);
                ByteBufCodecs.VAR_INT.encode(buf, pkt.sootTicks);
            },
            buf -> new FlooTransitS2CPayload(
                    ByteBufCodecs.VAR_INT.decode(buf),
                    ByteBufCodecs.VAR_INT.decode(buf))
    );

    @Override
    public @NonNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void send(@NonNull ServerPlayer player, int durationTicks, int sootTicks) {
        PacketDistributor.sendToPlayer(player, new FlooTransitS2CPayload(durationTicks, sootTicks));
    }
}
