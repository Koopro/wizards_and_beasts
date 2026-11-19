package at.koopro.wizardsandbeasts.house;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * The wizard's answer to the Hat: let it decide, or plead against where it leans. The server works the lean out
 * again itself; the client only says which.
 */
public record SortingChoiceC2SPayload(boolean plead) implements CustomPacketPayload {

    public static final Type<SortingChoiceC2SPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "sorting_choice"));

    public static final StreamCodec<ByteBuf, SortingChoiceC2SPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, SortingChoiceC2SPayload::plead, SortingChoiceC2SPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(SortingChoiceC2SPayload pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (ctx.player() instanceof ServerPlayer player
                    && at.koopro.wizardsandbeasts.artefact.gryffindor.GryffindorService.wearsHat(player)) {
                HouseService.sort(player, pkt.plead());
            }
        });
    }
}
