package at.koopro.wizardsandbeasts.network.spell;
import at.koopro.wizardsandbeasts.network.PacketCodecUtils;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.spell.imperio.ImperioServerLogic;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jspecify.annotations.NonNull;

public record ImperioResistC2SPayload() implements CustomPacketPayload {
    public static final Type<ImperioResistC2SPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "imperio_resist_attempt"));

    public static final StreamCodec<ByteBuf, ImperioResistC2SPayload> STREAM_CODEC =
            PacketCodecUtils.noPayloadCodec(ImperioResistC2SPayload::new);

    @Override
    public @NonNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(ImperioResistC2SPayload pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer player)) return;
            ImperioServerLogic.onSneakResistAttempt(player);
        });
    }
}
