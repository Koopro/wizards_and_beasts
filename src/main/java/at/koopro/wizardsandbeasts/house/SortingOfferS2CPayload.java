package at.koopro.wizardsandbeasts.house;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.network.ClientScreenHooksInvoker;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** The Hat speaks: where it leans ({@code house}), and whether it is too sure to be argued with. */
public record SortingOfferS2CPayload(String house, boolean sure) implements CustomPacketPayload {

    public static final Type<SortingOfferS2CPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "sorting_offer"));

    public static final StreamCodec<ByteBuf, SortingOfferS2CPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.stringUtf8(32), SortingOfferS2CPayload::house,
            ByteBufCodecs.BOOL, SortingOfferS2CPayload::sure,
            SortingOfferS2CPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handleClient(SortingOfferS2CPayload pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> ClientScreenHooksInvoker.invoke("openSortingScreen",
                String.class, pkt.house(), boolean.class, pkt.sure()));
    }
}
