package at.koopro.wizardsandbeasts.network.currency;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.command.debug.DebugHooks;
import at.koopro.wizardsandbeasts.currency.vault.CurrencyHelper;
import at.koopro.wizardsandbeasts.currency.vault.GringottsTransaction;
import at.koopro.wizardsandbeasts.currency.vault.PlayerVaultData;
import at.koopro.wizardsandbeasts.registry.ModAttachments;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import at.koopro.wizardsandbeasts.registry.CurrencyItemRegistry;

public record VaultActionC2SPayload(int actionOrdinal, int amount) implements CustomPacketPayload {
    private static final int MAX_TRANSACTION_AMOUNT = 4096;

    public static final Type<VaultActionC2SPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "vault_action"));

    public static final StreamCodec<ByteBuf, VaultActionC2SPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public VaultActionC2SPayload decode(ByteBuf buf) {
            return new VaultActionC2SPayload(buf.readByte(), buf.readInt());
        }

        @Override
        public void encode(ByteBuf buf, VaultActionC2SPayload pkt) {
            buf.writeByte(pkt.actionOrdinal);
            buf.writeInt(pkt.amount);
        }
    };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(VaultActionC2SPayload pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer player)) return;
            if (pkt.actionOrdinal < 0 || pkt.actionOrdinal >= Action.values().length) return;
            if (pkt.amount <= 0 || pkt.amount > MAX_TRANSACTION_AMOUNT) return;

            Action action = Action.values()[pkt.actionOrdinal];
            PlayerVaultData vault = player.getData(ModAttachments.VAULT_DATA.get());

            switch (action) {
                case DEPOSIT_KNUT -> {
                    if (!GringottsTransaction.isAcceptedCoin(new net.minecraft.world.item.ItemStack(CurrencyItemRegistry.KNUT.get()))) return;
                    int have = CurrencyHelper.countItem(player.getInventory(), CurrencyItemRegistry.KNUT.get());
                    int toDeposit = Math.min(pkt.amount, have);
                    if (toDeposit > 0 && CurrencyHelper.removeItems(player.getInventory(), CurrencyItemRegistry.KNUT.get(), toDeposit)) {
                        vault.depositKnuts(toDeposit);
                    }
                }
                case DEPOSIT_SICKLE -> {
                    if (!GringottsTransaction.isAcceptedCoin(new net.minecraft.world.item.ItemStack(CurrencyItemRegistry.SICKLE.get()))) return;
                    int have = CurrencyHelper.countItem(player.getInventory(), CurrencyItemRegistry.SICKLE.get());
                    int toDeposit = Math.min(pkt.amount, have);
                    if (toDeposit > 0 && CurrencyHelper.removeItems(player.getInventory(), CurrencyItemRegistry.SICKLE.get(), toDeposit)) {
                        vault.depositSickles(toDeposit);
                    }
                }
                case DEPOSIT_GALLEON -> {
                    if (!GringottsTransaction.isAcceptedCoin(new net.minecraft.world.item.ItemStack(CurrencyItemRegistry.GALLEON.get()))) return;
                    int have = CurrencyHelper.countItem(player.getInventory(), CurrencyItemRegistry.GALLEON.get());
                    int toDeposit = Math.min(pkt.amount, have);
                    if (toDeposit > 0 && CurrencyHelper.removeItems(player.getInventory(), CurrencyItemRegistry.GALLEON.get(), toDeposit)) {
                        vault.depositGalleons(toDeposit);
                    }
                }
                case DEPOSIT_ALL -> {
                    if (!GringottsTransaction.isAcceptedCoin(new net.minecraft.world.item.ItemStack(CurrencyItemRegistry.KNUT.get()))
                            || !GringottsTransaction.isAcceptedCoin(new net.minecraft.world.item.ItemStack(CurrencyItemRegistry.SICKLE.get()))
                            || !GringottsTransaction.isAcceptedCoin(new net.minecraft.world.item.ItemStack(CurrencyItemRegistry.GALLEON.get()))) {
                        return;
                    }
                    int haveK = CurrencyHelper.countItem(player.getInventory(), CurrencyItemRegistry.KNUT.get());
                    int haveS = CurrencyHelper.countItem(player.getInventory(), CurrencyItemRegistry.SICKLE.get());
                    int haveG = CurrencyHelper.countItem(player.getInventory(), CurrencyItemRegistry.GALLEON.get());
                    if (haveK > 0) {
                        CurrencyHelper.removeItems(player.getInventory(), CurrencyItemRegistry.KNUT.get(), haveK);
                        vault.depositKnuts(haveK);
                    }
                    if (haveS > 0) {
                        CurrencyHelper.removeItems(player.getInventory(), CurrencyItemRegistry.SICKLE.get(), haveS);
                        vault.depositSickles(haveS);
                    }
                    if (haveG > 0) {
                        CurrencyHelper.removeItems(player.getInventory(), CurrencyItemRegistry.GALLEON.get(), haveG);
                        vault.depositGalleons(haveG);
                    }
                }
                case WITHDRAW_KNUT -> {
                    // Smart withdraw: auto-breaks sickles/galleons if not enough knuts
                    long withdrawn = vault.withdrawSmartKnuts(pkt.amount);
                    if (withdrawn > 0) {
                        CurrencyHelper.giveItems(player.getInventory(), CurrencyItemRegistry.KNUT.get(), (int) withdrawn);
                    }
                }
                case WITHDRAW_SICKLE -> {
                    // Smart withdraw: auto-breaks galleons if not enough sickles
                    long withdrawn = vault.withdrawSmartSickles(pkt.amount);
                    if (withdrawn > 0) {
                        CurrencyHelper.giveItems(player.getInventory(), CurrencyItemRegistry.SICKLE.get(), (int) withdrawn);
                    }
                }
                case WITHDRAW_GALLEON -> {
                    int toWithdraw = (int) Math.min(pkt.amount, vault.getGalleons());
                    if (toWithdraw > 0 && vault.withdrawGalleons(toWithdraw)) {
                        CurrencyHelper.giveItems(player.getInventory(), CurrencyItemRegistry.GALLEON.get(), toWithdraw);
                    }
                }
                case WITHDRAW_ALL -> {
                    long k = vault.getKnuts();
                    long s = vault.getSickles();
                    long g = vault.getGalleons();
                    if (k > 0) { vault.withdrawKnuts(k); CurrencyHelper.giveItems(player.getInventory(), CurrencyItemRegistry.KNUT.get(), (int) k); }
                    if (s > 0) { vault.withdrawSickles(s); CurrencyHelper.giveItems(player.getInventory(), CurrencyItemRegistry.SICKLE.get(), (int) s); }
                    if (g > 0) { vault.withdrawGalleons(g); CurrencyHelper.giveItems(player.getInventory(), CurrencyItemRegistry.GALLEON.get(), (int) g); }
                }
                case EXCHANGE_KNUTS_TO_SICKLE -> vault.exchangeKnutsToSickle();
                case EXCHANGE_SICKLE_TO_KNUTS -> vault.exchangeSickleToKnuts();
                case EXCHANGE_SICKLES_TO_GALLEON -> vault.exchangeSicklesToGalleon();
                case EXCHANGE_GALLEON_TO_SICKLES -> vault.exchangeGalleonToSickles();
            }
            DebugHooks.logVault(player, action.name(), pkt.amount, vault.getKnuts(), vault.getSickles(), vault.getGalleons());

            GringottsOpenS2CPayload.sendToPlayer(player);
        });
    }

    public enum Action {
        DEPOSIT_KNUT,
        DEPOSIT_SICKLE,
        DEPOSIT_GALLEON,
        DEPOSIT_ALL,
        WITHDRAW_KNUT,
        WITHDRAW_SICKLE,
        WITHDRAW_GALLEON,
        WITHDRAW_ALL,
        EXCHANGE_KNUTS_TO_SICKLE,
        EXCHANGE_SICKLE_TO_KNUTS,
        EXCHANGE_SICKLES_TO_GALLEON,
        EXCHANGE_GALLEON_TO_SICKLES
    }
}
