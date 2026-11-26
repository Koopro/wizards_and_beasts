package at.koopro.wizardsandbeasts.network.wand;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.wand.gui.WandmakersBenchMenu;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Client to server: the player pressed the bench's act button. The server decides whether the act begins. */
public record BenchActPayload(int containerId) implements CustomPacketPayload {

    public static final Type<BenchActPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "bench_act"));

    public static final StreamCodec<ByteBuf, BenchActPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, BenchActPayload::containerId,
            BenchActPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(BenchActPayload pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (ctx.player() instanceof ServerPlayer player
                    && player.containerMenu instanceof WandmakersBenchMenu menu
                    && menu.containerId == pkt.containerId) {
                menu.beginAct(player);
            }
        });
    }
}
