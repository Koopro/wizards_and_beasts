package at.koopro.wizardsandbeasts.network.form;
import at.koopro.wizardsandbeasts.network.heritage.HeritageDataSyncS2CPayload;
import at.koopro.wizardsandbeasts.network.PacketCodecUtils;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.heritage.data.PlayerHeritageData;
import at.koopro.wizardsandbeasts.registry.ModAttachments;
import at.koopro.wizardsandbeasts.heritage.obscurial.ObscurialResourceManager;
import at.koopro.wizardsandbeasts.heritage.obscurial.ObscurialRules;
import at.koopro.wizardsandbeasts.heritage.Heritage;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record ObscurialStressVentC2SPayload() implements CustomPacketPayload {

    public static final Type<ObscurialStressVentC2SPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "obscurial_stress_vent"));

    public static final StreamCodec<ByteBuf, ObscurialStressVentC2SPayload> STREAM_CODEC =
            PacketCodecUtils.noPayloadCodec(ObscurialStressVentC2SPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(ObscurialStressVentC2SPayload pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer player)) return;
            PlayerHeritageData data = player.getData(ModAttachments.HERITAGE_DATA.get());
            if (data.getSelectedHeritage() != Heritage.OBSCURIAL) return;
            if (ObscurialRules.isDarkForm(data)) return;

            long now = player.level().getGameTime();
            long cooldownUntil = ObscurialResourceManager.getStressVentCooldownUntil(player);
            if (now < cooldownUntil) {
                long remain = Math.max(1L, (cooldownUntil - now) / 20L);
                player.displayClientMessage(Component.literal("\u00A75Stress Vent recovering: " + remain + "s"), true);
                return;
            }

            ObscurialResourceManager.reduceStress(player, ObscurialResourceManager.getStressVentRecovery());
            ObscurialResourceManager.setStressVentCooldownUntil(player, now + ObscurialResourceManager.getStressVentCooldownTicks());
            player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS,
                    ObscurialResourceManager.getStressVentDrawbackTicks(), 0, false, true, true));
            player.addEffect(new MobEffectInstance(MobEffects.SLOWNESS,
                    ObscurialResourceManager.getStressVentDrawbackTicks(), 0, false, true, true));
            player.displayClientMessage(Component.literal("\u00A7dYou vent obscurus strain, but feel drained."), true);
            HeritageDataSyncS2CPayload.syncToPlayer(player, false);
        });
    }
}
