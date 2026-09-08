package at.koopro.wizardsandbeasts.currency.debug;

import at.koopro.wizardsandbeasts.command.debug.feature.FeatureDebugSection;
import at.koopro.wizardsandbeasts.command.debug.report.DebugReport;
import at.koopro.wizardsandbeasts.currency.dragot.DragotQuotes;
import at.koopro.wizardsandbeasts.currency.dragot.DragotRates;
import at.koopro.wizardsandbeasts.currency.vault.PlayerVaultData;
import at.koopro.wizardsandbeasts.module.Module;
import at.koopro.wizardsandbeasts.registry.ModAttachments;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * The vault balance, and the Dragot rate this player is currently being quoted.
 *
 * <p>The quote matters because it is <em>per player and pinned</em> for a window of ticks, so two
 * wizards at the same counter get different numbers and neither is wrong. Without seeing the pinned
 * rate that reads as a bug every time.
 */
@NullMarked
public final class GringottsFeatureDebug implements FeatureDebugSection {

    @Override
    public String id() {
        return "gringotts";
    }

    @Override
    public String title() {
        return "Gringotts";
    }

    @Override
    public String summary() {
        return "Vault balance in all three coins, and this player's pinned Dragot quote.";
    }

    @Override
    public @Nullable Module module() {
        return Module.GRINGOTTS;
    }

    @Override
    public void append(DebugReport report, ServerPlayer target, Detail detail) {
        PlayerVaultData vault = target.getData(ModAttachments.VAULT_DATA.get());
        report.row("  vault", vault.getGalleons() + "g " + vault.getSickles()
                + "s " + vault.getKnuts() + "k");
        report.row("  total (knuts)", vault.getTotalInKnuts());
        if (detail == Detail.BRIEF) {
            return;
        }

        report.section("  dragot exchange");
        float rate = DragotQuotes.rateFor(target);
        report.row("    quoted rate", String.format("%.4f", rate));
        report.row("    drift from base", String.format("%+.1f%%",
                DragotQuotes.driftPercent(target) * 100f));
        report.row("    quote lifetime", DragotRates.QUOTE_LIFETIME_TICKS + "t");
        report.row("    gringotts fee", String.format("%.0f%%", DragotRates.GRINGOTTS_FEE * 100));
        report.row("    affordable now", DragotRates.dragotsAffordable(vault.getTotalInKnuts(), rate)
                + " dragots");
    }
}
