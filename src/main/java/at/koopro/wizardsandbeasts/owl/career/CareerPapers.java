package at.koopro.wizardsandbeasts.owl.career;

import at.koopro.wizardsandbeasts.feedback.NoticeKind;
import at.koopro.wizardsandbeasts.feedback.PlayerFeedback;
import at.koopro.wizardsandbeasts.item.trinket.MinistryLicenseScrollItem;
import at.koopro.wizardsandbeasts.ministry.law.TraceService;
import at.koopro.wizardsandbeasts.ministry.licence.LicenseType;
import at.koopro.wizardsandbeasts.ministry.licence.MinistryLicences;
import at.koopro.wizardsandbeasts.owl.OWLGrade;
import at.koopro.wizardsandbeasts.owl.OWLSubject;
import at.koopro.wizardsandbeasts.owl.Profession;
import at.koopro.wizardsandbeasts.registry.TrinketItemRegistry;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.Map;

/**
 * The Ministry's paperwork after an O.W.L. sitting or a career choice: a rank-0 licence for each
 * type {@link CareerProfile#papersFor} grants and the candidate does not already carry.
 *
 * <p>Before this, nothing in the game ever wrote a licence onto a scroll
 * ({@link MinistryLicenseScrollItem#issue} had no caller), so with the Ministry module on every gate
 * that asks for papers — Gringotts' counter, racing brooms, restricted ingredients — refused everyone.
 * Higher ranks are still earned the existing way: ink, parchment and the grade ladder in
 * {@code LicenceUpgrade}.
 *
 * <p>Only while the Ministry is watching ({@link TraceService#isActive()}): with it off no gate reads
 * a licence, and handing out documents nobody checks would be clutter. A licence the holder already
 * carries — even a revoked one — is not reissued, so a sitting cannot launder a hearing's revocation.
 */
@NullMarked
public final class CareerPapers {

    private CareerPapers() {}

    /** Issues what is owed. Returns how many documents were handed over. */
    public static int issue(ServerPlayer player, Map<OWLSubject, OWLGrade> grades, @Nullable Profession career) {
        if (!TraceService.isActive()) return 0;
        int issued = 0;
        for (LicenseType type : CareerProfile.papersFor(grades, career)) {
            if (MinistryLicences.findDocument(player, type) != null) continue;
            ItemStack scroll = MinistryLicenseScrollItem.issue(
                    new ItemStack(TrinketItemRegistry.MINISTRY_LICENSE_SCROLL.get()), type, player.getUUID());
            at.koopro.wizardsandbeasts.util.InventoryHandover.giveOrDrop(player, scroll);
            PlayerFeedback.toast(player, NoticeKind.UNLOCK,
                    Component.translatable("owls.career.papers.title"),
                    Component.translatable("owls.career.papers.body", type.displayName()));
            issued++;
        }
        return issued;
    }
}
