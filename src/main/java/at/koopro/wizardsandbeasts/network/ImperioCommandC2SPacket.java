package at.koopro.wizardsandbeasts.network;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.spell.imperio.ImperioCommand;
import at.koopro.wizardsandbeasts.spell.imperio.ImperioServerLogic;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jspecify.annotations.NonNull;

public record ImperioCommandC2SPacket(int commandOrdinal) implements CustomPacketPayload {
    public static final Type<ImperioCommandC2SPacket> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "imperio_command"));

    public static final StreamCodec<ByteBuf, ImperioCommandC2SPacket> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, ImperioCommandC2SPacket::commandOrdinal,
            ImperioCommandC2SPacket::new);

    @Override
    public @NonNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(ImperioCommandC2SPacket pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer player)) return;
            ImperioCommand cmd = ImperioCommand.byOrdinal(pkt.commandOrdinal);
            ImperioServerLogic.applyCommand(player, cmd);
        });
    }
}
