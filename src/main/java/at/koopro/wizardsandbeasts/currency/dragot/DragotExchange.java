package at.koopro.wizardsandbeasts.currency.dragot;

import at.koopro.wizardsandbeasts.currency.vault.CurrencyHelper;
import at.koopro.wizardsandbeasts.currency.vault.PlayerVaultData;
import at.koopro.wizardsandbeasts.ministry.law.MagicalOffence;
import at.koopro.wizardsandbeasts.ministry.law.TraceService;
import at.koopro.wizardsandbeasts.registry.ModAttachments;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.NullMarked;

/**
 * Changing money at the counter.
 *
 * <p>Both directions go through the vault rather than through the player's pockets: Gringotts is a
 * bank, and a teller who handed you loose Knuts across the desk would be a bureau de change. It also
 * means the fee and the spread are applied to a number the vault already owns, so no rounding can
 * leak coins into or out of the world.
 *
 * <p><b>Selling is where a forgery gets looked at.</b> A goblin taking your Dragots counts them, and
 * {@link DragotRates#DEVALUED_NOTICE_CHANCE} of the time notices one is wrong — at which point the
 * whole transaction fails, the bad coins are confiscated, and it goes on your Ministry file. That is
 * the reputation hit: {@link TraceService#report} already funnels into the standing system's
 * {@code OFFENCE} deed trigger, so passing bad money moves the same needle every other crime does
 * without a second reputation counter existing.
 */
@NullMarked
public final class DragotExchange {

    /** What happened at the counter. {@code message} is presentable either way. */
    public record Result(boolean ok, Component message) {

        static Result no(String key, Object... args) {
            return new Result(false, Component.translatable(key, args));
        }
    }

    private DragotExchange() {}

    /**
     * Sells {@code dragots} for vault Knuts at the wizard's standing quote.
     *
     * <p>Worst coins first ({@link DragotPurse#take}), so a purse with any bad money in it meets the
     * notice roll immediately rather than eventually.
     */
    public static Result sell(ServerPlayer player, int dragots) {
        if (dragots <= 0) {
            return Result.no("currency.wizards_and_beasts.dragot.exchange.nothing");
        }
        if (DragotPurse.total(player) < dragots) {
            return Result.no("currency.wizards_and_beasts.dragot.exchange.short");
        }

        float rate = DragotQuotes.rateFor(player);
        int badTaken = DragotPurse.take(player, dragots);
        if (badTaken < 0) {
            return Result.no("currency.wizards_and_beasts.dragot.exchange.short");
        }

        if (badTaken > 0 && player.getRandom().nextFloat() < DragotRates.DEVALUED_NOTICE_CHANCE) {
            // Caught. The coins are already out of the purse and stay out — confiscated, not refunded.
            reportBadMoney(player);
            return Result.no("currency.wizards_and_beasts.dragot.exchange.caught", badTaken);
        }

        long knuts = DragotRates.dragotsToKnuts(dragots, rate);
        vault(player).depositKnuts(knuts);
        return new Result(true, Component.translatable("currency.wizards_and_beasts.dragot.exchange.sold",
                dragots, CurrencyHelper.formatFromKnuts(knuts)));
    }

    /** Buys {@code dragots} with vault Knuts at the wizard's standing quote. */
    public static Result buy(ServerPlayer player, int dragots) {
        if (dragots <= 0) {
            return Result.no("currency.wizards_and_beasts.dragot.exchange.nothing");
        }
        float rate = DragotQuotes.rateFor(player);
        long cost = DragotRates.knutsToBuyDragots(dragots, rate);
        PlayerVaultData vault = vault(player);
        if (vault.getTotalInKnuts() < cost) {
            return Result.no("currency.wizards_and_beasts.dragot.exchange.funds",
                    CurrencyHelper.formatFromKnuts(cost));
        }
        if (vault.withdrawSmartKnuts(cost) < cost) {
            return Result.no("currency.wizards_and_beasts.dragot.exchange.funds",
                    CurrencyHelper.formatFromKnuts(cost));
        }
        DragotPurse.give(player, dragots);
        return new Result(true, Component.translatable("currency.wizards_and_beasts.dragot.exchange.bought",
                dragots, CurrencyHelper.formatFromKnuts(cost)));
    }

    /**
     * The 20% roll for a vendor who has just been handed Dragots outside the bank.
     *
     * <p>Separate from {@link #sell} because the consequences differ: a shopkeeper refuses the sale
     * and remembers you, where a goblin confiscates the coin. Both file the same offence.
     *
     * @return {@code true} if the bad money was spotted
     */
    public static boolean noticedByVendor(ServerPlayer player) {
        if (DragotPurse.devalued(player) <= 0) {
            return false;
        }
        if (player.getRandom().nextFloat() >= DragotRates.DEVALUED_NOTICE_CHANCE) {
            return false;
        }
        reportBadMoney(player);
        return true;
    }

    /** One place files the crime, so the two detection paths cannot drift apart. */
    private static void reportBadMoney(ServerPlayer player) {
        TraceService.report(player, MagicalOffence.PASSING_DEVALUED_COIN);
    }

    private static PlayerVaultData vault(ServerPlayer player) {
        return player.getData(ModAttachments.VAULT_DATA.get());
    }
}
