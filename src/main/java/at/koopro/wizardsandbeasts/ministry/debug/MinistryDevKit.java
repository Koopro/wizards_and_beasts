package at.koopro.wizardsandbeasts.ministry.debug;

import at.koopro.wizardsandbeasts.command.debug.dev.DevLog;
import at.koopro.wizardsandbeasts.command.debug.dev.FeatureDevKit;
import at.koopro.wizardsandbeasts.ministry.MinistryRecords;
import at.koopro.wizardsandbeasts.ministry.data.MinistryRank;
import at.koopro.wizardsandbeasts.ministry.data.PlayerMinistryRecord;
import at.koopro.wizardsandbeasts.ministry.licence.LicenseType;
import at.koopro.wizardsandbeasts.ministry.licence.MinistryLicences;
import at.koopro.wizardsandbeasts.registry.ModAttachments;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.NullMarked;

/**
 * A clean record and the authority to act on other people's.
 *
 * <p>Rank goes to {@code MINISTER} because the interesting half of the Ministry layer is
 * enforcement — arresting, pardoning, appointing — and every one of those is gated behind a rank
 * you cannot otherwise give yourself without knowing the exact command. Notoriety and fines are
 * cleared for the opposite reason: a developer testing anything else does not want Aurors arriving.
 *
 * <p><b>Licences are not granted here.</b> A licence is a document in the inventory, deliberately —
 * losable, stealable, forgeable — and there is no flag to flip. Writing one would mean minting
 * paperwork, which is {@code /wandb ministry licence grant}'s job and not a side effect a setup
 * command should have. What this does instead is <em>report</em> which ones you are missing, so the
 * next command is obvious.
 */
@NullMarked
public final class MinistryDevKit implements FeatureDevKit {

    @Override
    public String id() {
        return "ministry";
    }

    @Override
    public String title() {
        return "Ministry";
    }

    @Override
    public String summary() {
        return "Clean record, Minister rank, and a list of the licences you are still missing.";
    }

    @Override
    public void open(ServerPlayer target, DevLog log) {
        PlayerMinistryRecord before = target.getData(ModAttachments.MINISTRY_RECORD.get());
        MinistryRecords.mutate(target, record -> record
                .withNotoriety(0f)
                .withRank(MinistryRank.MINISTER)
                .withOutstandingFine(0L)
                .withFugitive(false)
                .withSentenceTicks(0));
        log.changed("rank", MinistryRank.MINISTER);
        if (before.notoriety() > 0f) {
            log.changed("notoriety cleared", "was " + String.format("%.1f", before.notoriety()));
        }
        if (before.owesFine()) {
            log.changed("fine waived", before.outstandingFineKnuts() + " knuts");
        }
        if (before.isServingSentence()) {
            log.changed("sentence cleared", before.sentenceTicks() + "t remaining");
        }
        reportMissingLicences(target, log);
    }

    @Override
    public void reset(ServerPlayer target, DevLog log) {
        MinistryRecords.set(target, PlayerMinistryRecord.DEFAULT);
        log.changed("ministry record reset", "no rank, no record, no debt");
    }

    /** Asked the same way a gate asks, so what it reports is what a gate would refuse. */
    private static void reportMissingLicences(ServerPlayer target, DevLog log) {
        StringBuilder missing = new StringBuilder();
        for (LicenseType type : LicenseType.values()) {
            if (MinistryLicences.has(target, type)) {
                continue;
            }
            if (!missing.isEmpty()) {
                missing.append(", ");
            }
            missing.append(type.name().toLowerCase(java.util.Locale.ROOT));
        }
        if (missing.isEmpty()) {
            log.skip("holds every licence");
        } else {
            log.skip("no licence for: " + missing + " - /wandb ministry licence grant <type>");
        }
    }
}
