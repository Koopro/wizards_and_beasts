package at.koopro.wizardsandbeasts.network.wand;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.wand.gui.WandmakersBenchMenu;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record SetFlexibilityPayload(int containerId, int flexibilityOrdinal) implements CustomPacketPayload {

    public static final Type<SetFlexibilityPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "set_flexibility"));

    public static final StreamCodec<ByteBuf, SetFlexibilityPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, SetFlexibilityPayload::containerId,
            ByteBufCodecs.VAR_INT, SetFlexibilityPayload::flexibilityOrdinal,
            SetFlexibilityPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(SetFlexibilityPayload pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (ctx.player().containerMenu instanceof WandmakersBenchMenu menu
                    && menu.containerId == pkt.containerId) {
                menu.setFlexibilityOrdinalFromServer(pkt.flexibilityOrdinal);
            }
        });
    }
}
