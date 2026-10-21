package at.koopro.wizardsandbeasts.network.location;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.location.LocationView;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jspecify.annotations.NullMarked;

/**
 * S→C: a location's board, built by the server ({@code LocationProgression#view}). With {@code open} the client
 * shows it (a host was spoken to); without, a board already open for that place refreshes and nothing else happens.
 */
@NullMarked
public record LocationViewS2CPayload(LocationView view, boolean open) implements CustomPacketPayload {

    public static final Type<LocationViewS2CPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "location_view"));

    public static final StreamCodec<ByteBuf, LocationViewS2CPayload> STREAM_CODEC = StreamCodec.composite(
            LocationView.STREAM_CODEC, LocationViewS2CPayload::view,
            ByteBufCodecs.BOOL, LocationViewS2CPayload::open,
            LocationViewS2CPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void send(ServerPlayer player, LocationView view, boolean open) {
        PacketDistributor.sendToPlayer(player, new LocationViewS2CPayload(view, open));
    }
}
