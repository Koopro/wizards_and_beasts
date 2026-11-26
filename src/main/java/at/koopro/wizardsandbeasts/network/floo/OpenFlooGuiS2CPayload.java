package at.koopro.wizardsandbeasts.network.floo;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.floo.FlooDestinationDto;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jspecify.annotations.NonNull;

import java.util.List;

/**
 * Opens the Floo screen.
 *
 * <p>{@code originAddress} is the name of the grate the player is standing at, so the screen can say
 * where the call or the journey is coming <em>from</em>. Empty when the hearth has no address, which
 * cannot normally happen — an unregistered hearth never opens this screen — but is possible for a
 * hearth unregistered by command between the click and the packet.
 */
public record OpenFlooGuiS2CPayload(@NonNull List<FlooDestinationDto> destinations,
                                    @NonNull String originAddress) implements CustomPacketPayload {

    public static final Type<OpenFlooGuiS2CPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "open_floo_gui_s2c"));

    public static final StreamCodec<ByteBuf, OpenFlooGuiS2CPayload> STREAM_CODEC = StreamCodec.of(
            (buf, pkt) -> {
                FlooDestinationDto.LIST_STREAM_CODEC.encode(buf, pkt.destinations);
                ByteBufCodecs.STRING_UTF8.encode(buf, pkt.originAddress);
            },
            buf -> new OpenFlooGuiS2CPayload(
                    FlooDestinationDto.LIST_STREAM_CODEC.decode(buf),
                    ByteBufCodecs.STRING_UTF8.decode(buf))
    );

    @Override
    public @NonNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void send(@NonNull ServerPlayer player, @NonNull List<FlooDestinationDto> destinations,
                            @NonNull String originAddress) {
        PacketDistributor.sendToPlayer(player,
                new OpenFlooGuiS2CPayload(destinations, originAddress));
    }
}
