package at.koopro.wizardsandbeasts.owl.debug;

import at.koopro.wizardsandbeasts.command.debug.feature.FeatureDebugSection;
import at.koopro.wizardsandbeasts.command.debug.report.DebugReport;
import at.koopro.wizardsandbeasts.module.Module;
import at.koopro.wizardsandbeasts.owl.data.PlayerOWLData;
import at.koopro.wizardsandbeasts.registry.ModAttachments;
import at.koopro.wizardsandbeasts.stats.PlayerStat;
import at.koopro.wizardsandbeasts.stats.PlayerStatsAPI;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Exam results, and the Knowledge that gates sitting them.
 *
 * <p>{@code Module.OWLS} is the <em>examination</em> system. It is not owl post, which is a separate
 * scheduled hand-off with its own commands — the two get confused often enough that saying so here
 * is worth a line.
 */
@NullMarked
public final class OwlFeatureDebug implements FeatureDebugSection {

    @Override
    public String id() {
        return "owls";
    }

    @Override
    public String title() {
        return "O.W.L.s";
    }

    @Override
    public String summary() {
        return "Exam grades per subject and the Knowledge score behind them (not owl post).";
    }

    @Override
    public @Nullable Module module() {
        return Module.OWLS;
    }

    @Override
    public void append(DebugReport report, ServerPlayer target, Detail detail) {
        PlayerOWLData data = target.getData(ModAttachments.OWL_DATA.get());
        report.flag("  exam taken", data.examTaken());
        report.row("  subjects graded", data.grades().size());
        report.row("  knowledge", PlayerStatsAPI.getStat(target, PlayerStat.KNOWLEDGE));
        if (detail == Detail.BRIEF) {
            return;
        }
        report.section("  grades");
        if (data.grades().isEmpty()) {
            report.row("    -", "none sat");
        } else {
            data.grades().forEach((subject, grade) -> report.row("    " + subject, String.valueOf(grade)));
        }
    }
}
