package at.koopro.wizardsandbeasts.wand.refine;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** The refinement action the player picked at the bench; the server re-derives everything from it. */
public record SetRefineActionPayload(int containerId, int actionOrdinal) implements CustomPacketPayload {

    public static final Type<SetRefineActionPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "set_refine_action"));

    public static final StreamCodec<ByteBuf, SetRefineActionPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, SetRefineActionPayload::containerId,
            ByteBufCodecs.VAR_INT, SetRefineActionPayload::actionOrdinal,
            SetRefineActionPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(SetRefineActionPayload pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (ctx.player().containerMenu instanceof WandRefineMenu menu && menu.containerId == pkt.containerId()) {
                menu.chooseAction(pkt.actionOrdinal());
            }
        });
    }
}
