package at.koopro.wizardsandbeasts.network.bloodpact;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
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
}
