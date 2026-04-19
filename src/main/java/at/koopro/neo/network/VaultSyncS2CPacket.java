package at.koopro.neo.network;

import at.koopro.neo.Neo;
import at.koopro.neo.data.PlayerVaultData;
import at.koopro.neo.registry.ModAttachments;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record VaultSyncS2CPacket(long knuts, long sickles, long galleons) implements CustomPacketPayload {

    public static final Type<VaultSyncS2CPacket> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(Neo.MODID, "vault_sync"));

    public static final StreamCodec<ByteBuf, VaultSyncS2CPacket> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public VaultSyncS2CPacket decode(ByteBuf buf) {
            return new VaultSyncS2CPacket(buf.readLong(), buf.readLong(), buf.readLong());
        }

        @Override
        public void encode(ByteBuf buf, VaultSyncS2CPacket pkt) {
            buf.writeLong(pkt.knuts);
            buf.writeLong(pkt.sickles);
            buf.writeLong(pkt.galleons);
        }
    };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handleClient(VaultSyncS2CPacket pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> ClientVaultDataHolder.load(pkt.knuts, pkt.sickles, pkt.galleons));
    }

    public static void syncToPlayer(ServerPlayer player) {
        PlayerVaultData vault = player.getData(ModAttachments.VAULT_DATA.get());
        PacketDistributor.sendToPlayer(player,
                new VaultSyncS2CPacket(vault.getKnuts(), vault.getSickles(), vault.getGalleons()));
    }
}
