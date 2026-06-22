package at.koopro.wizardsandbeasts.network.form;

import at.koopro.wizardsandbeasts.network.PacketCodecUtils;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.ability.AnimagusTransformService;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Client → server request to toggle the player's Animagus beast form. */
public record AnimagusTransformC2SPayload() implements CustomPacketPayload {

    public static final Type<AnimagusTransformC2SPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "animagus_transform"));

    public static final StreamCodec<ByteBuf, AnimagusTransformC2SPayload> STREAM_CODEC =
            PacketCodecUtils.noPayloadCodec(AnimagusTransformC2SPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(AnimagusTransformC2SPayload pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer player)) return;
            AnimagusTransformService.toggleTransform(player);
        });
    }
}
