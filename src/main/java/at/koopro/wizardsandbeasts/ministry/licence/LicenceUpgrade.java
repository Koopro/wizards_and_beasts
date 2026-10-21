package at.koopro.wizardsandbeasts.ministry.licence;

import at.koopro.wizardsandbeasts.Config;
import at.koopro.wizardsandbeasts.currency.billing.GringottsBilling;
import at.koopro.wizardsandbeasts.currency.billing.MoneyFlow;
import at.koopro.wizardsandbeasts.currency.vault.CurrencyHelper;
import at.koopro.wizardsandbeasts.module.Module;
import at.koopro.wizardsandbeasts.module.ModuleManager;
import at.koopro.wizardsandbeasts.owl.OWLGrade;
import at.koopro.wizardsandbeasts.owl.data.PlayerOWLData;
import at.koopro.wizardsandbeasts.registry.MiscItemRegistry;
import at.koopro.wizardsandbeasts.registry.ModAttachments;
import at.koopro.wizardsandbeasts.registry.ModDataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Writing a higher endorsement onto a licence you already hold.
 *
 * <p>Ink and parchment are the materials; the O.W.L. is the permission. Both are required and they
 * fail differently on purpose — running out of ink is a shopping problem and gets a "you need ink"
 * message, while failing the exam is a <em>you</em> problem and names the grade you were short of.
 * A player who is told only "cannot upgrade" learns nothing about which of the two they are missing.
 *
 * <p>The parchment and ink are consumed only on a success. A refused endorsement costs nothing,
 * because the Ministry does not keep your stationery for telling you no. The Ministry's fee ({@link #fee}, billed
 * through Gringotts) is taken last, after every other requirement is met, and only while Gringotts is open.
 *
 * <p><b>No N.E.W.T.s.</b> The brief asked for "OWL/NEWT knowledge flags"; the repo has O.W.L.s
 * ({@link PlayerOWLData}) and no N.E.W.T. system at all. Rather than stub one, the ladder is built
 * out of O.W.L. grade bands — see {@link LicenceRules#gradeRequiredFor} — so the requirement is real
 * and earnable today instead of pointing at a flag nothing sets.
 */
@NullMarked
public final class LicenceUpgrade {

    /** Why an endorsement attempt ended the way it did. */
    public record Outcome(boolean upgraded, Component message) {

        static Outcome no(String key, Object... args) {
            return new Outcome(false, Component.translatable(key, args));
        }
    }

    private LicenceUpgrade() {}

    /**
     * Attempts to endorse {@code scroll} one rank higher for {@code player}.
     *
     * <p>Materials are consumed and the component rewritten only when every gate passes.
     */
    public static Outcome attempt(ServerPlayer player, ItemStack scroll) {
        LicenseData data = MinistryLicences.read(scroll);
        if (data == null) {
            return Outcome.no("ministry.wizards_and_beasts.licence.upgrade.blank");
        }
        if (data.revoked()) {
            return Outcome.no("ministry.wizards_and_beasts.licence.upgrade.revoked");
        }
        if (!data.issuedTo().equals(player.getUUID())) {
            return Outcome.no("ministry.wizards_and_beasts.licence.deny.not_yours");
        }

        int targetRank = data.rank() + 1;
        OWLGrade required = LicenceRules.gradeRequiredFor(targetRank);
        if (required == null) {
            return Outcome.no("ministry.wizards_and_beasts.licence.upgrade.max_rank", LicenseType.MAX_RANK);
        }

        OWLGrade held = gradeIn(player, data.type());
        if (!LicenceRules.gradeSatisfies(held, required)) {
            return Outcome.no("ministry.wizards_and_beasts.licence.upgrade.owl",
                    Component.translatable(data.type().examinedSubject().translationKey()),
                    Component.translatable(required.translationKey()),
                    Component.translatable(held.translationKey()));
        }

        int parchmentSlot = findMaterial(player, MiscItemRegistry.PARCHMENT.get());
        if (parchmentSlot < 0) {
            return Outcome.no("ministry.wizards_and_beasts.licence.upgrade.no_parchment");
        }
        int inkSlot = findMaterial(player, MiscItemRegistry.INK_BOTTLE.get());
        if (inkSlot < 0) {
            return Outcome.no("ministry.wizards_and_beasts.licence.upgrade.no_ink");
        }

        // The Ministry's fee, last of all: every other refusal has been given, so a wizard is never billed for an
        // endorsement they were not going to get. A short vault takes nothing and keeps the parchment and ink.
        long fee = feeFor(targetRank);
        if (!GringottsBilling.charge(player, MoneyFlow.LICENCE_ENDORSEMENT, fee)) {
            return Outcome.no("ministry.wizards_and_beasts.licence.upgrade.fee", CurrencyHelper.formatFromKnuts(fee));
        }

        player.getInventory().removeItem(parchmentSlot, 1);
        player.getInventory().removeItem(inkSlot, 1);
        scroll.set(ModDataComponents.LICENSE_DATA.get(), data.withRank(targetRank));

        return new Outcome(true, fee > 0
                ? Component.translatable("ministry.wizards_and_beasts.licence.upgrade.done_paid",
                        data.type().displayName(), targetRank, CurrencyHelper.formatFromKnuts(fee))
                : Component.translatable("ministry.wizards_and_beasts.licence.upgrade.done",
                        data.type().displayName(), targetRank));
    }

    /**
     * The fee for endorsing a licence to {@code targetRank} when the per-rank fee is {@code feePerRank}: rank × fee,
     * so the ladder costs 1 + 2 + 3 = 6 fees in all. 0 for a rank below 1 or no fee. Pure.
     */
    public static long fee(int targetRank, long feePerRank) {
        if (targetRank < 1 || feePerRank <= 0) {
            return 0L;
        }
        return Math.multiplyExact(feePerRank, (long) targetRank);
    }

    /** What endorsing to {@code targetRank} costs now: nothing while Gringotts is closed. */
    public static long feeFor(int targetRank) {
        if (!ModuleManager.isEnabled(Module.GRINGOTTS)) {
            return 0L;
        }
        return fee(targetRank, Config.licenceEndorsementFeeKnuts);
    }

    /** The player's grade in the subject this licence is examined on; {@link OWLGrade#T} if untested. */
    public static OWLGrade gradeIn(ServerPlayer player, LicenseType type) {
        PlayerOWLData owls = player.getData(ModAttachments.OWL_DATA.get());
        return owls.grades().getOrDefault(type.examinedSubject(), OWLGrade.T);
    }

    /** Slot holding one of {@code item}, or {@code -1}. */
    private static int findMaterial(ServerPlayer player, net.minecraft.world.item.Item item) {
        var inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            if (inventory.getItem(slot).is(item)) {
                return slot;
            }
        }
        return -1;
    }

    /** The grade an endorsement to {@code targetRank} needs, for display; {@code null} past the cap. */
    public static @Nullable OWLGrade requirementFor(int targetRank) {
        return LicenceRules.gradeRequiredFor(targetRank);
    }
}
