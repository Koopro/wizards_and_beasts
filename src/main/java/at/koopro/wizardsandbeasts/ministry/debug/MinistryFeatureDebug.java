package at.koopro.wizardsandbeasts.ministry.debug;

import at.koopro.wizardsandbeasts.command.debug.feature.FeatureDebugSection;
import at.koopro.wizardsandbeasts.command.debug.report.DebugReport;
import at.koopro.wizardsandbeasts.ministry.law.MagicalOffence;
import at.koopro.wizardsandbeasts.ministry.data.PlayerMinistryRecord;
import at.koopro.wizardsandbeasts.ministry.law.TraceService;
import at.koopro.wizardsandbeasts.ministry.licence.LicenseType;
import at.koopro.wizardsandbeasts.ministry.licence.MinistryLicences;
import at.koopro.wizardsandbeasts.module.Module;
import at.koopro.wizardsandbeasts.registry.ModAttachments;
import at.koopro.wizardsandbeasts.util.ChatPalette;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.Map;

/**
 * The Ministry's file on this wizard: heat, record, debts and papers.
 *
 * <p>Licences are checked live rather than read from the record, because a licence is a
 * <em>document in the inventory</em>, not a flag — the whole point of the layer is that it can be
 * lost, stolen or forged. So the row for each type asks {@link MinistryLicences#verdict} the same
 * question the gate asks, and prints the reason it gives. A gate refusing while a player insists
 * they hold the licence is nearly always an expired or under-endorsed document, and the verdict
 * says which.
 */
@NullMarked
public final class MinistryFeatureDebug implements FeatureDebugSection {

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
        return "Notoriety, wanted level, offences, sentence, fines, rank and live licence verdicts.";
    }

    @Override
    public @Nullable Module module() {
        return Module.MINISTRY;
    }

    @Override
    public void append(DebugReport report, ServerPlayer target, Detail detail) {
        PlayerMinistryRecord record = target.getData(ModAttachments.MINISTRY_RECORD.get());

        report.flag("  trace active", TraceService.isActive());
        report.bar("  notoriety", record.notoriety() / 100f);
        report.state("  wanted level", String.valueOf(record.wantedLevel()),
                record.wantedLevel() == null ? ChatPalette.MUTED : ChatPalette.WARN);
        report.row("  rank", String.valueOf(record.rank()));
        if (record.isServingSentence()) {
            report.state("  sentence", record.sentenceTicks() + "t remaining", ChatPalette.BAD);
        }
        if (record.fugitive()) {
            report.state("  fugitive", "yes - walked out of a sentence", ChatPalette.BAD);
        }
        if (record.owesFine()) {
            report.state("  outstanding fine", record.outstandingFineKnuts() + " knuts", ChatPalette.WARN);
        }
        if (detail == Detail.BRIEF) {
            return;
        }

        report.row("  offences (lifetime)", record.totalOffences());
        report.section("  record");
        Map<MagicalOffence, Integer> offences = record.offencesByWeight();
        if (offences.isEmpty()) {
            report.row("    -", "clean");
        } else {
            offences.forEach((offence, count) -> report.row("    " + offence,
                    count + "x, repeat multiplier x"
                            + String.format("%.2f", record.repeatMultiplier(offence))));
        }

        report.section("  authority");
        report.flag("    may arrest", record.rank().mayArrest());
        report.flag("    may pardon", record.rank().mayPardon());
        report.flag("    may appoint", record.rank().mayAppoint());

        // Asked, not read. See the class note.
        report.section("  licences (live verdict)");
        for (LicenseType type : LicenseType.values()) {
            MinistryLicences.Verdict verdict = MinistryLicences.verdict(target, type);
            report.state("    " + type, verdict.allowed() ? "allowed" : verdict.reason().getString(),
                    verdict.allowed() ? ChatPalette.OK : ChatPalette.BAD);
        }
    }
}
