package at.koopro.wizardsandbeasts.ministry.law;

import at.koopro.wizardsandbeasts.Config;
import at.koopro.wizardsandbeasts.currency.vault.CurrencyHelper;
import at.koopro.wizardsandbeasts.currency.vault.PlayerVaultData;
import at.koopro.wizardsandbeasts.ministry.MinistryRecords;
import at.koopro.wizardsandbeasts.ministry.data.PlayerMinistryRecord;
import at.koopro.wizardsandbeasts.ministry.post.MinistryPost;
import at.koopro.wizardsandbeasts.module.Module;
import at.koopro.wizardsandbeasts.module.ModuleManager;
import at.koopro.wizardsandbeasts.network.currency.VaultSyncS2CPayload;
import at.koopro.wizardsandbeasts.registry.ModAttachments;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.NullMarked;

/**
 * The money half of Ministry enforcement: what a paperwork offence costs, and how it is collected.
 *
 * <p>The Ministry bills a Gringotts account, so a fine is <b>debited from the vault, not from the pocket</b>.
 * That is the point of the connection: the vault stops being a place coins are parked and becomes something
 * the world can reach into. A player with nothing in the vault is not blocked — the debt stands on the
 * record, freezes their notoriety decay, and is swept up automatically the moment funds appear.
 *
 * <p>Everything here is server-side and goes through {@link MinistryRecords#mutate}, so the record sync and
 * any future auditing hang off one seam. The arithmetic lives in {@link FineSchedule}, which is pure; this
 * class only reads {@link Config} and touches the world.
 */
@NullMarked
public final class MinistryFines {

    private MinistryFines() {}

    /**
     * True while fines can be assessed at all. Gated on Gringotts as well as the Ministry: with no vault
     * to bill, a fine would be a debt nobody could ever pay, which freezes notoriety decay permanently.
     * The same reasoning gates the skill respec fee.
     */
    public static boolean isActive() {
        return ModuleManager.isEnabled(Module.MINISTRY)
                && ModuleManager.isEnabled(Module.GRINGOTTS)
                && Config.ministryFineScalePercent > 0;
    }

    /**
     * Files a fine for an offence just recorded. Called from {@link TraceService#report} — never directly,
     * so there stays exactly one place where an offence enters the system.
     *
     * @param priorsMultiplier the offender's repeat multiplier <em>before</em> this offence was filed, so
     *                         the first conviction is unscaled
     * @return the amount added to the debt, or 0 when this offence carries no fine
     */
    public static long assessFor(ServerPlayer offender, MagicalOffence offence, float priorsMultiplier) {
        if (!isActive() || !offence.fineable()) {
            return 0L;
        }
        long amount = FineSchedule.assess(offence.fineKnuts(), priorsMultiplier, Config.ministryFineScalePercent);
        if (amount <= 0L) {
            return 0L;
        }
        MinistryRecords.mutate(offender, record -> record.withFineAdjusted(amount));
        MinistryPost.send(offender,
                Component.translatable("ministry.wizards_and_beasts.notice.fine.subject"),
                Component.translatable("ministry.wizards_and_beasts.notice.fine.body",
                        offence.displayName(), money(amount)));

        // Take it straight away if the money is already there, so the common case never leaves a debt
        // sitting on the record for the player to discover later.
        collect(offender);
        return amount;
    }

    /**
     * Sweeps whatever the vault can cover against the outstanding debt. Partial payment is intentional:
     * the Ministry takes what is there and keeps the remainder on the books.
     *
     * @return the amount actually collected this call
     */
    public static long collect(ServerPlayer player) {
        return pay(player, Long.MAX_VALUE);
    }

    /**
     * Settles at most {@code maxKnuts} of the debt from the vault. The automatic sweep passes no limit;
     * a player paying by hand can choose to clear part of it.
     *
     * @return the amount actually taken
     */
    public static long pay(ServerPlayer player, long maxKnuts) {
        PlayerMinistryRecord record = MinistryRecords.get(player);
        if (!record.owesFine() || maxKnuts <= 0L || !ModuleManager.isEnabled(Module.GRINGOTTS)) {
            return 0L;
        }
        PlayerVaultData vault = player.getData(ModAttachments.VAULT_DATA.get());
        long ceiling = Math.min(record.outstandingFineKnuts(), maxKnuts);
        long take = FineSchedule.collectable(ceiling, vault.getTotalInKnuts());
        if (take <= 0L) {
            return 0L;
        }
        long withdrawn = vault.withdrawSmartKnuts(take);
        if (withdrawn <= 0L) {
            return 0L;
        }
        MinistryRecords.mutate(player, r -> r.withFineAdjusted(-withdrawn));
        VaultSyncS2CPayload.syncToPlayer(player);

        long remaining = MinistryRecords.get(player).outstandingFineKnuts();
        if (remaining > 0L) {
            MinistryPost.notify(player,
                    Component.translatable("ministry.wizards_and_beasts.notice.fine.part_paid",
                            money(withdrawn), money(remaining)),
                    ChatFormatting.YELLOW);
        } else {
            MinistryPost.notify(player,
                    Component.translatable("ministry.wizards_and_beasts.notice.fine.settled", money(withdrawn)),
                    ChatFormatting.GREEN);
        }
        return withdrawn;
    }

    /** Clears a debt without payment — a Ministry decision, not a player one. */
    public static void waive(ServerPlayer player) {
        if (!MinistryRecords.get(player).owesFine()) {
            return;
        }
        MinistryRecords.mutate(player, r -> r.withOutstandingFine(0L));
        MinistryPost.send(player,
                Component.translatable("ministry.wizards_and_beasts.notice.fine.waived.subject"),
                Component.translatable("ministry.wizards_and_beasts.notice.fine.waived.body"));
    }

    /** What the player currently owes, in Knuts. */
    public static long owed(ServerPlayer player) {
        return MinistryRecords.get(player).outstandingFineKnuts();
    }

    /** Formats a Knut amount as a denomination breakdown, for notices and command output. */
    public static Component money(long knuts) {
        return Component.literal(CurrencyHelper.formatFromKnuts(knuts));
    }
}
