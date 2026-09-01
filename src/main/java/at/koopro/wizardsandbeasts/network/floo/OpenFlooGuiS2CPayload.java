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
 * <p>{@code headInFire} distinguishes the two things a lit grate is for. False is travel: you are
 * about to step into the fire. True is a call — you have knelt and put your head in it, and what
 * comes back is a conversation rather than a journey. The screen changes shape for it, and the
 * request it sends on confirm is a different packet entirely.
 *
 * <p>{@code originAddress} is the name of the grate the player is standing at, so the screen can say
 * where the call or the journey is coming <em>from</em>. Empty when the hearth has no address, which
 * cannot normally happen — an unregistered hearth never opens this screen — but is possible for a
 * hearth unregistered by command between the click and the packet.
 */
public record OpenFlooGuiS2CPayload(@NonNull List<FlooDestinationDto> destinations,
                                    @NonNull String originAddress,
                                    boolean headInFire) implements CustomPacketPayload {

    public static final Type<OpenFlooGuiS2CPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "open_floo_gui_s2c"));

    public static final StreamCodec<ByteBuf, OpenFlooGuiS2CPayload> STREAM_CODEC = StreamCodec.of(
            (buf, pkt) -> {
                FlooDestinationDto.LIST_STREAM_CODEC.encode(buf, pkt.destinations);
                ByteBufCodecs.STRING_UTF8.encode(buf, pkt.originAddress);
                ByteBufCodecs.BOOL.encode(buf, pkt.headInFire);
            },
            buf -> new OpenFlooGuiS2CPayload(
                    FlooDestinationDto.LIST_STREAM_CODEC.decode(buf),
                    ByteBufCodecs.STRING_UTF8.decode(buf),
                    ByteBufCodecs.BOOL.decode(buf))
    );

    @Override
    public @NonNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void send(@NonNull ServerPlayer player, @NonNull List<FlooDestinationDto> destinations,
                            @NonNull String originAddress, boolean headInFire) {
        PacketDistributor.sendToPlayer(player,
                new OpenFlooGuiS2CPayload(destinations, originAddress, headInFire));
    }
}
