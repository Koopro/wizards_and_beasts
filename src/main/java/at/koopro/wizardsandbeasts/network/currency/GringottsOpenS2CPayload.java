package at.koopro.wizardsandbeasts.network.currency;
import at.koopro.wizardsandbeasts.network.PacketCodecUtils;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.currency.vault.PlayerVaultData;
import at.koopro.wizardsandbeasts.registry.ModAttachments;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

public record GringottsOpenS2CPayload(long knuts, long sickles, long galleons) implements CustomPacketPayload {

    public static final Type<GringottsOpenS2CPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "gringotts_open"));

    public static final StreamCodec<ByteBuf, GringottsOpenS2CPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public GringottsOpenS2CPayload decode(ByteBuf buf) {
            PacketCodecUtils.CurrencyAmounts amounts = PacketCodecUtils.readCurrencyAmounts(buf);
            return new GringottsOpenS2CPayload(amounts.knuts(), amounts.sickles(), amounts.galleons());
        }

        @Override
        public void encode(ByteBuf buf, GringottsOpenS2CPayload pkt) {
            PacketCodecUtils.writeCurrencyAmounts(buf, pkt.knuts, pkt.sickles, pkt.galleons);
        }
    };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void sendToPlayer(ServerPlayer player) {
        PlayerVaultData vault = player.getData(ModAttachments.VAULT_DATA.get());
        PacketDistributor.sendToPlayer(player,
                new GringottsOpenS2CPayload(vault.getKnuts(), vault.getSickles(), vault.getGalleons()));
    }
}
