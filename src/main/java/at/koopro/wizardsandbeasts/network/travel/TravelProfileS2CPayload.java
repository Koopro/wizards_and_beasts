package at.koopro.wizardsandbeasts.network.travel;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.travel.profile.TravelProfile;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NullMarked;

/** S→C: the owner's own {@link TravelProfile}, for the Travel tab. Sent only to the player it describes. */
@NullMarked
public record TravelProfileS2CPayload(TravelProfile profile) implements CustomPacketPayload {

    public static final Type<TravelProfileS2CPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "travel_profile"));

    public static final StreamCodec<ByteBuf, TravelProfileS2CPayload> STREAM_CODEC =
            TravelProfile.STREAM_CODEC.map(TravelProfileS2CPayload::new, TravelProfileS2CPayload::profile);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
