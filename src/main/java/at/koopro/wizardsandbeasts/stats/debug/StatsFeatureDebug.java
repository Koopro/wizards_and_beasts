package at.koopro.wizardsandbeasts.stats.debug;

import at.koopro.wizardsandbeasts.command.debug.feature.FeatureDebugSection;
import at.koopro.wizardsandbeasts.command.debug.report.DebugReport;
import at.koopro.wizardsandbeasts.module.Module;
import at.koopro.wizardsandbeasts.registry.ModAttachments;
import at.koopro.wizardsandbeasts.stats.PlayerStat;
import at.koopro.wizardsandbeasts.stats.PlayerStatsAPI;
import at.koopro.wizardsandbeasts.stats.PlayerStatsData;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * The four trained stats, the one derived one, and where they are capped.
 *
 * <p>KNOWLEDGE is derived and only <em>snapshotted</em> into the attachment, so the stored value and
 * the recomputed one can differ — that is not a bug on its own, but it is the first thing to check
 * when a player's Knowledge does not move after reading a book. Both are printed, side by side.
 */
@NullMarked
public final class StatsFeatureDebug implements FeatureDebugSection {

    @Override
    public String id() {
        return "stats";
    }

    @Override
    public String title() {
        return "Player Stats";
    }

    @Override
    public String summary() {
        return "Power/Precision/Willpower/Reflexes, derived Knowledge, caps and training.";
    }

    @Override
    public @Nullable Module module() {
        return Module.PLAYER_STATS;
    }

    @Override
    public void append(DebugReport report, ServerPlayer target, Detail detail) {
        PlayerStatsData data = target.getData(ModAttachments.PLAYER_STATS.get());
        if (data.isEmpty()) {
            report.warn("  stats attachment is empty — nothing has ever written to it");
        }
        for (PlayerStat stat : PlayerStat.values()) {
            report.row("  " + stat.getId(), data.get(stat));
        }
        if (detail == Detail.BRIEF) {
            return;
        }

        report.flag("  prodigy", data.isProdigy());

        report.section("  power ceiling");
        report.row("    cap", PlayerStatsAPI.getPowerCap(target));
        report.row("    growth accumulated", data.powerGrowthAccumulated());
        report.row("    growth remaining", PlayerStatsAPI.getRemainingPowerGrowth(target));

        // Stored against recomputed. See the class note.
        report.section("  knowledge");
        int stored = data.get(PlayerStat.KNOWLEDGE);
        int computed = PlayerStatsAPI.computeKnowledge(target);
        report.row("    stored", stored);
        report.row("    recomputed", computed);
        if (stored != computed) {
            report.note("    snapshot is behind — it refreshes on the next sync");
        }

        report.section("  training progress");
        data.trainingProgress().forEach((stat, progress) -> report.bar("    " + stat.getId(), progress));
        if (data.trainingProgress().isEmpty()) {
            report.row("    —", "nothing part-trained");
        }
    }
}
