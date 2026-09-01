package at.koopro.wizardsandbeasts.ministry.licence;

import at.koopro.wizardsandbeasts.broom.BroomDefinition;
import at.koopro.wizardsandbeasts.broom.BroomTier;
import at.koopro.wizardsandbeasts.feedback.PlayerFeedback;
import at.koopro.wizardsandbeasts.ministry.law.TraceService;
import net.minecraft.ChatFormatting;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import org.jspecify.annotations.NullMarked;

/**
 * The broom half of the licence, in one place because a broom has two ways to be mounted.
 *
 * <p>{@code BroomItem.use} spawns one and rides it; {@code BroomEntity.interact} mounts one already
 * standing there. Both end at {@code startRiding}, and a rule enforced at only one of them is a rule
 * with a documented bypass, so both call {@link #refuse}.
 *
 * <p>Only bites while {@link TraceService#isActive()} — the Ministry module is what cares about
 * paperwork, and a server playing without it should not find half its brooms locked.
 */
@NullMarked
public final class BroomLicence {

    private BroomLicence() {}

    /**
     * Whether this mount should be refused, and tells the rider why if so.
     *
     * @return {@code true} when the player may <em>not</em> ride
     */
    public static boolean refuse(Player player, BroomDefinition definition) {
        if (!(player instanceof ServerPlayer rider)) {
            return false;
        }
        if (!TraceService.isActive()) {
            return false;
        }
        BroomTier tier = definition.tier();
        if (!LicenceRules.requiresBroomLicence(tier)) {
            return false;
        }
        MinistryLicences.Verdict verdict = MinistryLicences.verdict(rider, LicenseType.BROOM);
        if (verdict.allowed()) {
            return false;
        }
        PlayerFeedback.actionBar(rider, verdict.reason().copy().withStyle(ChatFormatting.RED));
        return true;
    }
}
