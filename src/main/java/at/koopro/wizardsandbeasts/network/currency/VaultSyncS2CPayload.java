package at.koopro.wizardsandbeasts.network.currency;
import at.koopro.wizardsandbeasts.network.PacketCodecUtils;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.client.currency.state.ClientVaultDataState;
import at.koopro.wizardsandbeasts.currency.vault.PlayerVaultData;
import at.koopro.wizardsandbeasts.registry.ModAttachments;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import java.util.concurrent.atomic.AtomicInteger;

public record VaultSyncS2CPayload(int syncVersion, long knuts, long sickles, long galleons) implements CustomPacketPayload {
    private static final AtomicInteger NEXT_SYNC_VERSION = new AtomicInteger();

    public static final Type<VaultSyncS2CPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "vault_sync"));

    public static final StreamCodec<ByteBuf, VaultSyncS2CPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public VaultSyncS2CPayload decode(ByteBuf buf) {
            int syncVersion = PacketCodecUtils.clampNonNegative(buf.readInt());
            PacketCodecUtils.CurrencyAmounts amounts = PacketCodecUtils.readCurrencyAmounts(buf);
            return new VaultSyncS2CPayload(syncVersion, amounts.knuts(), amounts.sickles(), amounts.galleons());
        }

        @Override
        public void encode(ByteBuf buf, VaultSyncS2CPayload pkt) {
            buf.writeInt(PacketCodecUtils.clampNonNegative(pkt.syncVersion));
            PacketCodecUtils.writeCurrencyAmounts(buf, pkt.knuts, pkt.sickles, pkt.galleons);
        }
    };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handleClient(VaultSyncS2CPayload pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> ClientVaultDataState.load(pkt.syncVersion, pkt.knuts, pkt.sickles, pkt.galleons));
    }

    public static void syncToPlayer(ServerPlayer player) {
        PlayerVaultData vault = player.getData(ModAttachments.VAULT_DATA.get());
        PacketDistributor.sendToPlayer(player,
                new VaultSyncS2CPayload(
                        NEXT_SYNC_VERSION.incrementAndGet(),
                        vault.getKnuts(),
                        vault.getSickles(),
                        vault.getGalleons()));
    }
}
