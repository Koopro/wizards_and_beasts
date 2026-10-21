package at.koopro.wizardsandbeasts.admin.report;

import at.koopro.wizardsandbeasts.admin.AdminCategory;
import at.koopro.wizardsandbeasts.admin.operation.EconomyOperations;
import at.koopro.wizardsandbeasts.admin.report.ReportView.Tone;
import at.koopro.wizardsandbeasts.currency.vault.CurrencyHelper;
import at.koopro.wizardsandbeasts.market.EconomyReport;
import org.jspecify.annotations.NullMarked;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Economy section reports. {@code economy_markets}: the trader books {@link EconomyReport} builds for the Markets tab
 * and {@code /wandb admin economy market}, pointed at one market, with the audited reset as a button. The flows and
 * the ledger stay on the Markets tab; this page is for acting on one market.
 */
@NullMarked
final class EconomyReports {

    private EconomyReports() {}

    static void registerAll() {
        AdminReports.register(AdminReport.of("economy_markets", AdminCategory.ECONOMY, EconomyReports::markets));
    }

    private static String path(String location) {
        int colon = location.indexOf(':');
        return colon < 0 ? location : location.substring(colon + 1);
    }

    private static void markets(AdminReport.Request request, ReportBuilder out) {
        EconomyReport.Snapshot snapshot = EconomyReport.build(request.server());
        Set<String> locations = new LinkedHashSet<>();
        snapshot.markets().forEach(row -> locations.add(path(row.location())));
        locations.forEach(location -> out.subjectOption(location, location, ""));
        String subject = request.subject();
        out.section("overview");
        out.row("markets", locations.size(), locations.isEmpty() ? Tone.WARN : Tone.NORMAL);
        out.row("net", CurrencyHelper.formatFromKnuts(Math.abs(snapshot.net())) + (snapshot.net() < 0 ? " drained" : " added"));
        if (subject.isEmpty()) {
            out.note("pick_market");
            out.actionOn(EconomyOperations.MARKET_RESET, "act_reset_all", "all", "", true);
            return;
        }
        out.literalSection(subject);
        snapshot.markets().stream().filter(row -> path(row.location()).equals(subject)).forEach(row ->
                out.literal((row.buying() ? "buys " : "sells ") + row.good(),
                        (row.unitKnuts() > 0 ? CurrencyHelper.formatFromKnuts(row.unitKnuts()) : "by worth")
                                + " · " + row.saturation() + "%" + (row.inDemand() ? " · in demand" : "")
                                + (row.ceilingPerDay() > 0 ? " · ≤" + CurrencyHelper.formatFromKnuts(row.ceilingPerDay()) + "/day" : "")
                                + " · " + row.deal().name().toLowerCase(Locale.ROOT),
                        row.saturation() >= 75 ? Tone.WARN : Tone.NORMAL));
        out.action(EconomyOperations.MARKET_RESET, "act_reset", "", true);
    }
}
