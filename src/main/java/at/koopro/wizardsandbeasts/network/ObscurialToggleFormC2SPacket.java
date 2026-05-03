package at.koopro.wizardsandbeasts.network;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.data.PlayerTypeData;
import at.koopro.wizardsandbeasts.form.TransitionManager;
import at.koopro.wizardsandbeasts.registry.ModAttachments;
import at.koopro.wizardsandbeasts.type.ObscurialRules;
import at.koopro.wizardsandbeasts.type.WizType;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record ObscurialToggleFormC2SPacket() implements CustomPacketPayload {

    public static final Type<ObscurialToggleFormC2SPacket> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "obscurial_toggle_form"));

    public static final StreamCodec<ByteBuf, ObscurialToggleFormC2SPacket> STREAM_CODEC =
            PacketCodecUtils.noPayloadCodec(ObscurialToggleFormC2SPacket::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(ObscurialToggleFormC2SPacket pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer player)) return;

            PlayerTypeData data = player.getData(ModAttachments.TYPE_DATA.get());
            if (data.getSelectedType() != WizType.OBSCURIAL) {
                return;
            }
            if (TransitionManager.isTransitioning(player.getUUID())) {
                return;
            }
            long gameTime = player.level().getGameTime();
            if (ObscurialRules.isTransformLockedOut(player, gameTime)) {
                long remainSec = Math.max(1L, (ObscurialRules.getLockoutUntilTick(player) - gameTime) / 20L);
                player.displayClientMessage(Component.literal("\u00A74Obscurus exhausted. You can re-form in " + remainSec + "s."), true);
                return;
            }

            String current = data.getActiveFormId();
            String target = "obscurial_dark".equals(current) ? "obscurial_human" : "obscurial_dark";
            if ("obscurial_dark".equals(target) && ObscurialRules.getDrain(player) <= 5.0f) {
                player.displayClientMessage(Component.literal("\u00A74Not enough control to safely summon obscurus form."), true);
                return;
            }
            boolean started = TransitionManager.startTransition(player, target);
            if (!started) {
                player.displayClientMessage(Component.literal("\u00A7cTransformation failed."), true);
            }
        });
    }
}
