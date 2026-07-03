package at.koopro.wizardsandbeasts.network.azkaban;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.registry.ModAttachments;
import io.netty.buffer.ByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NonNull;

public record AzkabanTrespasserSyncPayload(boolean tagged) implements CustomPacketPayload {

    public static final Type<AzkabanTrespasserSyncPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "azkaban_trespasser_sync"));

    public static final StreamCodec<ByteBuf, AzkabanTrespasserSyncPayload> STREAM_CODEC =
            new StreamCodec<>() {
                @Override
                public @NonNull AzkabanTrespasserSyncPayload decode(@NonNull ByteBuf buf) {
                    return new AzkabanTrespasserSyncPayload(buf.readBoolean());
                }

                @Override
                public void encode(@NonNull ByteBuf buf, @NonNull AzkabanTrespasserSyncPayload pkt) {
                    buf.writeBoolean(pkt.tagged);
                }
            };

    @Override
    public @NonNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /** Pushes the player's persisted trespasser flag to their client (login/respawn/dimension change). */
    public static void syncToPlayer(@NonNull ServerPlayer player) {
        boolean tagged = player.getData(ModAttachments.AZKABAN_TRESPASSER_TAG.get()).tagged();
        PacketDistributor.sendToPlayer(player, new AzkabanTrespasserSyncPayload(tagged));
    }
}
