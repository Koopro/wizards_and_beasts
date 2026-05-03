package at.koopro.wizardsandbeasts.network;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.data.PlayerTypeData;
import at.koopro.wizardsandbeasts.registry.ModAttachments;
import at.koopro.wizardsandbeasts.type.ObscurialRules;
import at.koopro.wizardsandbeasts.type.WizType;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record ObscurialStressVentC2SPacket() implements CustomPacketPayload {

    public static final Type<ObscurialStressVentC2SPacket> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "obscurial_stress_vent"));

    public static final StreamCodec<ByteBuf, ObscurialStressVentC2SPacket> STREAM_CODEC =
            PacketCodecUtils.noPayloadCodec(ObscurialStressVentC2SPacket::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(ObscurialStressVentC2SPacket pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer player)) return;
            PlayerTypeData data = player.getData(ModAttachments.TYPE_DATA.get());
            if (data.getSelectedType() != WizType.OBSCURIAL) return;
            if (ObscurialRules.isDarkForm(data)) return;

            long now = player.level().getGameTime();
            long cooldownUntil = ObscurialRules.getStressVentCooldownUntil(player);
            if (now < cooldownUntil) {
                long remain = Math.max(1L, (cooldownUntil - now) / 20L);
                player.displayClientMessage(Component.literal("\u00A75Stress Vent recovering: " + remain + "s"), true);
                return;
            }

            ObscurialRules.reduceStress(player, ObscurialRules.getStressVentRecovery());
            ObscurialRules.setStressVentCooldownUntil(player, now + ObscurialRules.getStressVentCooldownTicks());
            player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS,
                    ObscurialRules.getStressVentDrawbackTicks(), 0, false, true, true));
            player.addEffect(new MobEffectInstance(MobEffects.SLOWNESS,
                    ObscurialRules.getStressVentDrawbackTicks(), 0, false, true, true));
            player.displayClientMessage(Component.literal("\u00A7dYou vent obscurus strain, but feel drained."), true);
            TypeDataSyncS2CPacket.syncToPlayer(player, false);
        });
    }
}
