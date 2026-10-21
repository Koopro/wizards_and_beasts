package at.koopro.wizardsandbeasts.currency.billing;

import at.koopro.wizardsandbeasts.currency.vault.PlayerVaultData;
import at.koopro.wizardsandbeasts.network.currency.VaultSyncS2CPayload;
import at.koopro.wizardsandbeasts.registry.ModAttachments;
import com.mojang.logging.LogUtils;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.NullMarked;
import org.slf4j.Logger;

/**
 * The one door through which gameplay moves money into or out of a vault.
 *
 * <p>Every service bills through {@link #charge}, every payment arrives through {@link #credit}, and a service that
 * fails after it was paid gives the money back through {@link #refund}. Each call:
 *
 * <ul>
 *   <li>is all or nothing — a refused charge leaves every coin of every denomination where it was
 *       ({@link PlayerVaultData#pay});</li>
 *   <li>records the flow in the world's {@link GringottsLedger};</li>
 *   <li>resynchronises the player's vault, so the Gringotts screen and the character sheet never show a balance the
 *       server no longer holds.</li>
 * </ul>
 *
 * <p>Whether a service costs anything at all (the Gringotts module, a price of 0) is the caller's decision, made
 * before it gets here; a price of 0 is always paid.
 */
@NullMarked
public final class GringottsBilling {

    private static final Logger LOGGER = LogUtils.getLogger();

    private GringottsBilling() {}

    /**
     * Takes {@code knuts} from the player's vault for {@code flow}, all or nothing.
     *
     * @return whether it was paid (always, for 0)
     */
    public static boolean charge(ServerPlayer player, MoneyFlow flow, long knuts) {
        requireDirection(flow, false);
        if (knuts < 0) {
            throw new IllegalArgumentException("negative charge " + knuts + " for " + flow.id());
        }
        if (knuts == 0) {
            return true;
        }
        if (!vault(player).pay(knuts)) {
            return false;
        }
        settle(player, flow, knuts, false);
        return true;
    }

    /**
     * Pays {@code knuts} into the player's vault for {@code flow}, in the largest coins.
     *
     * @return whether it was paid; refused only at the vault ceiling, which play never reaches
     */
    public static boolean credit(ServerPlayer player, MoneyFlow flow, long knuts) {
        requireDirection(flow, true);
        if (knuts < 0) {
            throw new IllegalArgumentException("negative credit " + knuts + " for " + flow.id());
        }
        if (knuts == 0) {
            return true;
        }
        if (!vault(player).credit(knuts)) {
            LOGGER.warn("[Gringotts] Refused a {} of {} Knuts to {}: the vault is at its ceiling",
                    flow.id(), knuts, player.getName().getString());
            return false;
        }
        settle(player, flow, knuts, false);
        return true;
    }

    /**
     * Gives back a charge whose service then failed, in the largest coins, and takes it off the ledger. Only for
     * money this call path charged a moment ago.
     */
    public static void refund(ServerPlayer player, MoneyFlow flow, long knuts) {
        requireDirection(flow, false);
        if (knuts <= 0) {
            return;
        }
        if (!vault(player).credit(knuts)) {
            // The vault held this money a moment ago; only a credit racing in between could fill it. Never lose it.
            LOGGER.error("[Gringotts] Refund of {} Knuts ({}) to {} hit the vault ceiling",
                    knuts, flow.id(), player.getName().getString());
            return;
        }
        settle(player, flow, knuts, true);
    }

    /**
     * Books an administrator's change that was made coin by coin (deposit 3 Galleons, withdraw 10 Knuts), so it shows
     * in the ledger apart from gameplay, and resyncs the vault. Moves no money itself.
     */
    public static void recordAdjustment(ServerPlayer player, MoneyFlow flow, long knuts) {
        if (flow.kind() != MoneyFlow.Kind.ADMIN) {
            throw new IllegalArgumentException(flow.id() + " is not an administrative flow");
        }
        if (knuts > 0) {
            settle(player, flow, knuts, false);
        }
    }

    /**
     * Books coins moved between a pocket and the vault at the counter ({@link MoneyFlow.Kind#CIRCULATION}). The
     * counter has already moved them coin by coin; this only records it, so pocket money entering the bank (loot,
     * Niffler hoards, player trade) is visible next to the money the game itself makes.
     */
    public static void recordCirculation(ServerPlayer player, MoneyFlow flow, long knuts) {
        if (flow.kind() != MoneyFlow.Kind.CIRCULATION) {
            throw new IllegalArgumentException(flow.id() + " is not a circulation flow");
        }
        if (knuts > 0) {
            book(player, flow, knuts, false);
        }
    }

    private static void settle(ServerPlayer player, MoneyFlow flow, long knuts, boolean reversal) {
        book(player, flow, knuts, reversal);
        VaultSyncS2CPayload.syncToPlayer(player);
    }

    /** The ledger's totals and the player's journal, kept in step: both see every movement or neither does. */
    private static void book(ServerPlayer player, MoneyFlow flow, long knuts, boolean reversal) {
        MinecraftServer server = player.level().getServer();
        if (server != null) {
            GringottsLedger ledger = GringottsLedger.get(server);
            if (reversal) {
                ledger.reverse(flow, knuts);
            } else {
                ledger.record(flow, knuts);
            }
        }
        player.setData(ModAttachments.MONEY_JOURNAL.get(), player.getData(ModAttachments.MONEY_JOURNAL.get())
                .with(new MoneyJournal.Entry(flow.id(), reversal ? -knuts : knuts, player.level().getGameTime())));
    }

    private static void requireDirection(MoneyFlow flow, boolean credit) {
        if (flow.credits() != credit) {
            throw new IllegalArgumentException(flow.id() + (credit ? " is not a credit" : " is not a charge"));
        }
    }

    private static PlayerVaultData vault(ServerPlayer player) {
        return player.getData(ModAttachments.VAULT_DATA.get());
    }
}
