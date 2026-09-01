package at.koopro.wizardsandbeasts.ministry.licence;

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
 * because the Ministry does not keep your stationery for telling you no.
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

        player.getInventory().removeItem(parchmentSlot, 1);
        player.getInventory().removeItem(inkSlot, 1);
        scroll.set(ModDataComponents.LICENSE_DATA.get(), data.withRank(targetRank));

        return new Outcome(true, Component.translatable(
                "ministry.wizards_and_beasts.licence.upgrade.done", data.type().displayName(), targetRank));
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
