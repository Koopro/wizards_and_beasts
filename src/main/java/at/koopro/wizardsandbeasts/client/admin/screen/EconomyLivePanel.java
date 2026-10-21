package at.koopro.wizardsandbeasts.client.admin.screen;

import at.koopro.wizardsandbeasts.admin.AdminCategory;
import at.koopro.wizardsandbeasts.client.admin.ClientAdminOpsState;
import at.koopro.wizardsandbeasts.client.admin.widget.AdminTheme;
import at.koopro.wizardsandbeasts.currency.vault.CurrencyHelper;
import at.koopro.wizardsandbeasts.market.DealClass;
import at.koopro.wizardsandbeasts.market.EconomyReport;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.NullMarked;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Economy → Markets: the money flows since the world began (sources against sinks — is this server inflating?) and
 * every open trader's book now: the next unit's price, how far trade has saturated it, today's short good, and the
 * most a good can ever pay the server per day. Read-only; asks every two seconds while shown. The same numbers as
 * {@code /wandb admin economy ledger} and {@code market}.
 */
@NullMarked
final class EconomyLivePanel extends OpsDocPanel {

    static final String KEY = "admin.wizards_and_beasts.economy_live.";

    @Override
    public AdminCategory section() {
        return AdminCategory.ECONOMY;
    }

    @Override
    protected Component title() {
        return Component.translatable(KEY + "title");
    }

    @Override
    protected Component summary() {
        return Component.translatable(KEY + "summary");
    }

    @Override
    protected int pollTicks() {
        return 40;
    }

    @Override
    protected void poll() {
        ClientAdminOpsState.requestEconomy();
    }

    @Override
    protected List<Line> lines() {
        List<Line> out = new ArrayList<>();
        EconomyReport.Snapshot snapshot = ClientAdminOpsState.economy();
        if (snapshot == null) {
            out.add(note("admin.wizards_and_beasts.status.loading"));
            return out;
        }
        out.add(heading(KEY + "flows"));
        String kind = "";
        for (EconomyReport.FlowLine flow : snapshot.flows()) {
            if (flow.count() == 0) {
                continue;
            }
            if (!flow.kind().equals(kind)) {
                kind = flow.kind();
                out.add(plain(Component.translatable(KEY + "kind." + kind).getString(), AdminTheme.RUBRIC));
            }
            out.add(plain("• " + Component.translatable("currency.wizards_and_beasts.flow." + flow.flow()).getString()
                    + " · " + flow.count() + "× · " + CurrencyHelper.formatFromKnuts(flow.knuts()), AdminTheme.INK_2));
        }
        out.add(Line.text(Component.translatable(KEY + "net", (snapshot.net() < 0 ? "−" : "")
                + CurrencyHelper.formatFromKnuts(Math.abs(snapshot.net()))),
                snapshot.net() > 0 ? AdminTheme.BAD : AdminTheme.GOOD));
        out.add(note(KEY + "net_note"));

        out.add(heading(KEY + "markets"));
        if (snapshot.markets().isEmpty()) {
            out.add(note(KEY + "no_markets"));
        }
        String location = "";
        for (EconomyReport.MarketRow row : snapshot.markets()) {
            if (!row.location().equals(location)) {
                location = row.location();
                String[] parts = location.split(":", 2);
                out.add(plain(Component.translatable("location." + parts[0] + "." + (parts.length > 1 ? parts[1] : "")
                        + ".name").getString(), AdminTheme.RUBRIC));
            }
            StringBuilder line = new StringBuilder("• ")
                    .append(Component.translatable(KEY + (row.buying() ? "buys" : "sells")).getString()).append(' ')
                    .append(row.good()).append(" · ")
                    .append(row.unitKnuts() > 0 ? CurrencyHelper.formatFromKnuts(row.unitKnuts())
                            : Component.translatable(KEY + "by_worth").getString())
                    .append(" · ").append(row.saturation()).append('%');
            if (row.inDemand()) {
                line.append(" · ").append(Component.translatable(KEY + "in_demand").getString());
            }
            if (row.ceilingPerDay() > 0) {
                line.append(" · ≤").append(CurrencyHelper.formatFromKnuts(row.ceilingPerDay()))
                        .append(Component.translatable(KEY + "per_day").getString());
            }
            if (row.deal() != DealClass.LEGAL) {
                line.append(" · ").append(row.deal().name().toLowerCase(Locale.ROOT));
            }
            int colour = row.deal() == DealClass.LEGAL ? AdminTheme.INK_2
                    : row.deal() == DealClass.RESTRICTED ? AdminTheme.GOLD_DARK : AdminTheme.BAD;
            out.add(plain(line.toString(), colour));
        }
        out.add(note(KEY + "markets_note"));
        return out;
    }
}
