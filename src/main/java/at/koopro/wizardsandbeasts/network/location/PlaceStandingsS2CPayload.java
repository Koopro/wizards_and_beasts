package at.koopro.wizardsandbeasts.network.location;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.location.PlaceStanding;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NullMarked;

import java.util.List;

/** S→C: the owner's standing at every open place, built by {@code LocationProgression#standings}. */
@NullMarked
public record PlaceStandingsS2CPayload(List<PlaceStanding> places) implements CustomPacketPayload {

    public static final Type<PlaceStandingsS2CPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "place_standings"));

    public static final StreamCodec<ByteBuf, PlaceStandingsS2CPayload> STREAM_CODEC =
            PlaceStanding.LIST_CODEC.map(PlaceStandingsS2CPayload::new, PlaceStandingsS2CPayload::places);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
