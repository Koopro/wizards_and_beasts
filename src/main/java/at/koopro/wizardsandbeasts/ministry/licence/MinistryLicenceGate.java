package at.koopro.wizardsandbeasts.ministry.licence;

import at.koopro.wizardsandbeasts.feedback.PlayerFeedback;
import at.koopro.wizardsandbeasts.ministry.law.TraceService;
import net.minecraft.ChatFormatting;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.NullMarked;

/**
 * One dealing with a Ministry official, start to finish.
 *
 * <p>Everything that counts as "an official looks at your papers" goes through {@link #admit}, and
 * the ordering inside it is the whole design:
 *
 * <ol>
 *   <li><b>The forgery is inspected first.</b> A forged licence that is about to be caught must be
 *       caught <em>before</em> it is honoured, or a player could ride a forgery through the very
 *       interaction that detects it. {@link LicenceForgery#inspect} revokes it in place, so step two
 *       then reads a revoked document and refuses on its own terms.</li>
 *   <li><b>Then the verdict.</b> Missing, revoked, expired, someone else's, under-ranked — all one
 *       refusal to the player, all different reasons in the message.</li>
 * </ol>
 *
 * <p>Silent when {@link TraceService#isActive()} is false. With the Ministry module off nobody is
 * checking anybody's papers, which is the supported way to play without the bureaucracy — and the
 * forgery roll goes unspent rather than quietly burning a player's forgery in a world where it could
 * never have mattered.
 */
@NullMarked
public final class MinistryLicenceGate {

    private MinistryLicenceGate() {}

    /**
     * Whether this player is dealt with, at rank 0.
     *
     * @return {@code true} to proceed with the interaction
     */
    public static boolean admit(ServerPlayer player, LicenseType type) {
        return admit(player, type, 0);
    }

    /** Whether this player is dealt with, at a required endorsement rank. */
    public static boolean admit(ServerPlayer player, LicenseType type, int requiredRank) {
        if (!TraceService.isActive()) {
            return true;
        }
        LicenceForgery.inspect(player, type);

        MinistryLicences.Verdict verdict = MinistryLicences.verdict(player, type, requiredRank);
        if (!verdict.allowed()) {
            PlayerFeedback.actionBar(player, verdict.reason().copy().withStyle(ChatFormatting.RED));
            return false;
        }
        return true;
    }
}
