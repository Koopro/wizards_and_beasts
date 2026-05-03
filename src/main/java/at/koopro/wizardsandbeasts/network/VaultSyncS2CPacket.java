package at.koopro.wizardsandbeasts.network;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.client.state.ClientVaultDataState;
import at.koopro.wizardsandbeasts.data.PlayerVaultData;
import at.koopro.wizardsandbeasts.registry.ModAttachments;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import java.util.concurrent.atomic.AtomicInteger;

public record VaultSyncS2CPacket(int syncVersion, long knuts, long sickles, long galleons) implements CustomPacketPayload {
    private static final AtomicInteger NEXT_SYNC_VERSION = new AtomicInteger();

    public static final Type<VaultSyncS2CPacket> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "vault_sync"));

    public static final StreamCodec<ByteBuf, VaultSyncS2CPacket> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public VaultSyncS2CPacket decode(ByteBuf buf) {
            int syncVersion = PacketCodecUtils.clampNonNegative(buf.readInt());
            PacketCodecUtils.CurrencyAmounts amounts = PacketCodecUtils.readCurrencyAmounts(buf);
            return new VaultSyncS2CPacket(syncVersion, amounts.knuts(), amounts.sickles(), amounts.galleons());
        }

        @Override
        public void encode(ByteBuf buf, VaultSyncS2CPacket pkt) {
            buf.writeInt(PacketCodecUtils.clampNonNegative(pkt.syncVersion));
            PacketCodecUtils.writeCurrencyAmounts(buf, pkt.knuts, pkt.sickles, pkt.galleons);
        }
    };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handleClient(VaultSyncS2CPacket pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> ClientVaultDataState.load(pkt.syncVersion, pkt.knuts, pkt.sickles, pkt.galleons));
    }

    public static void syncToPlayer(ServerPlayer player) {
        PlayerVaultData vault = player.getData(ModAttachments.VAULT_DATA.get());
        PacketDistributor.sendToPlayer(player,
                new VaultSyncS2CPacket(
                        NEXT_SYNC_VERSION.incrementAndGet(),
                        vault.getKnuts(),
                        vault.getSickles(),
                        vault.getGalleons()));
    }
}
