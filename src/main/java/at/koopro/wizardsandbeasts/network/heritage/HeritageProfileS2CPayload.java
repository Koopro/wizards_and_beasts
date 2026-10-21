package at.koopro.wizardsandbeasts.network.heritage;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.heritage.identity.HeritageProfile;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NullMarked;

/** S→C: the owner's own {@link HeritageProfile}, for the Heritage tab. Sent only to the player it describes. */
@NullMarked
public record HeritageProfileS2CPayload(HeritageProfile profile) implements CustomPacketPayload {

    public static final Type<HeritageProfileS2CPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "heritage_profile"));

    public static final StreamCodec<ByteBuf, HeritageProfileS2CPayload> STREAM_CODEC =
            HeritageProfile.STREAM_CODEC.map(HeritageProfileS2CPayload::new, HeritageProfileS2CPayload::profile);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
