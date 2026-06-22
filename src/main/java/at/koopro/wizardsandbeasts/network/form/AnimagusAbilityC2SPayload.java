package at.koopro.wizardsandbeasts.network.form;

import at.koopro.wizardsandbeasts.network.PacketCodecUtils;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.ability.AnimagusAbilityService;
import at.koopro.wizardsandbeasts.ability.AnimagusForms;
import at.koopro.wizardsandbeasts.ability.PlayerAbilityHelper;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Client → server request to use the active ability of the player's current Animagus beast form. */
public record AnimagusAbilityC2SPayload() implements CustomPacketPayload {

    public static final Type<AnimagusAbilityC2SPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "animagus_ability"));

    public static final StreamCodec<ByteBuf, AnimagusAbilityC2SPayload> STREAM_CODEC =
            PacketCodecUtils.noPayloadCodec(AnimagusAbilityC2SPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(AnimagusAbilityC2SPayload pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer player)) return;
            if (!PlayerAbilityHelper.isCurrentlyTransformed(player)) return;
            String formId = PlayerAbilityHelper.getAnimagusFormId(player);
            if (!AnimagusForms.isAnimagusForm(formId)) return;
            AnimagusAbilityService.useActive(player, formId);
        });
    }
}
