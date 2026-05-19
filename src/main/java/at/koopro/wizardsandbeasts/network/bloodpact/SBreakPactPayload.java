package at.koopro.wizardsandbeasts.network.bloodpact;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.registry.ModSounds;
import io.netty.buffer.ByteBuf;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jspecify.annotations.NonNull;

import java.util.UUID;

public record SBreakPactPayload(UUID pactUUID) implements CustomPacketPayload {

    public static final Type<SBreakPactPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "break_pact"));

    public static final StreamCodec<ByteBuf, SBreakPactPayload> STREAM_CODEC = StreamCodec.composite(
            UUIDUtil.STREAM_CODEC, SBreakPactPayload::pactUUID,
            SBreakPactPayload::new);

    @Override
    public @NonNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void sendToPlayer(ServerPlayer player, UUID pactUUID) {
        PacketDistributor.sendToPlayer(player, new SBreakPactPayload(pactUUID));
    }

    public static void handleClient(SBreakPactPayload pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null || mc.level == null) return;
            mc.player.displayClientMessage(
                    Component.literal("Your blood pact has been broken by the other party.")
                            .withStyle(ChatFormatting.DARK_RED),
                    false);
            mc.level.playLocalSound(
                    mc.player.getX(), mc.player.getY(), mc.player.getZ(),
                    ModSounds.BLOOD_PACT_SHATTER.get(), SoundSource.PLAYERS, 1.2f, 0.8f, false);
        });
    }
}
