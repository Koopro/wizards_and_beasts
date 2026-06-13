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

public record SUpdateVialPayload(UUID pactUUID, UUID partnerUUID) implements CustomPacketPayload {

    public static final Type<SUpdateVialPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "update_vial"));

    public static final StreamCodec<ByteBuf, SUpdateVialPayload> STREAM_CODEC = StreamCodec.composite(
            UUIDUtil.STREAM_CODEC, SUpdateVialPayload::pactUUID,
            UUIDUtil.STREAM_CODEC, SUpdateVialPayload::partnerUUID,
            SUpdateVialPayload::new);

    @Override
    public @NonNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void sendToPlayer(ServerPlayer player, UUID pactUUID, UUID partnerUUID) {
        PacketDistributor.sendToPlayer(player, new SUpdateVialPayload(pactUUID, partnerUUID));
    }
}
