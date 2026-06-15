package at.koopro.wizardsandbeasts.network.trinket;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.mirror.MirrorSessionManager;
import at.koopro.wizardsandbeasts.network.PacketCodecUtils;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Client closes its end of an active mirror connection. */
public record MirrorCloseC2SPayload() implements CustomPacketPayload {

    public static final Type<MirrorCloseC2SPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "mirror_close"));

    public static final StreamCodec<ByteBuf, MirrorCloseC2SPayload> STREAM_CODEC =
            PacketCodecUtils.noPayloadCodec(MirrorCloseC2SPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(MirrorCloseC2SPayload pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (ctx.player() instanceof ServerPlayer player) {
                MirrorSessionManager.end(player.getUUID(), player.level().getServer(), "The connection fades.");
            }
        });
    }
}
